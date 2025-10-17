package com.ev07b.commands;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import io.netty.channel.Channel;
import io.netty.buffer.Unpooled;

import com.ev07b.model.EV07BMessage;
import com.ev07b.services.DeviceService;
import com.ev07b.repos.CommandLogRepository;
import com.ev07b.entities.CommandLogEntity;
import com.ev07b.net.FrameUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class DataCommandProcessor implements CommandProcessor {

    private static final int DATA_CMD = 0x01;
    private static final int KEY_DEVICE_ID = 0x01;
    private static final int KEY_ALARM_CODE = 0x02;
    private static final int KEY_GPS = 0x20;
    private static final int KEY_GENERAL_DATA = 0x24;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private CommandLogRepository logRepo;

    @Override
    public int commandId() {
        return DATA_CMD;
    }

    @Override
    public void handle(EV07BMessage msg, Channel ch) {
        String deviceId = msg.getDeviceId();
        byte[] body = msg.getPayload();
        deviceService.touch(deviceId);
        logRepo.save(new CommandLogEntity(deviceId, msg.getCommandId(), body));

        // Hold parsed fields
        Long genTimestamp = null; // from key 0x24 if present
        Double lat = null, lon = null;
        Integer fenceIndex = null; // 1..4
        Boolean fenceIn = null; // true=in, false=out

        // Parse keys: [cmd][keyLen][key][value...]*
        int i = 1; // skip command byte
        while (i < body.length) {
            if (i >= body.length) break;
            int keyLen = Byte.toUnsignedInt(body[i++]);
            if (keyLen < 1 || i + keyLen - 1 > body.length) break;
            int key = Byte.toUnsignedInt(body[i++]);
            int valueLen = keyLen - 1;
            int valOff = i;

            try {
                if (key == KEY_GPS && valueLen >= 8) {
                    ByteBuffer bb = ByteBuffer.wrap(body, valOff, valueLen).order(ByteOrder.LITTLE_ENDIAN);
                    int lat_i = bb.getInt();
                    int lon_i = bb.getInt();
                    lat = lat_i / 1e7;
                    lon = lon_i / 1e7;
                } else if (key == KEY_GENERAL_DATA && valueLen >= 4) {
                    ByteBuffer bb = ByteBuffer.wrap(body, valOff, valueLen).order(ByteOrder.LITTLE_ENDIAN);
                    int ts = bb.getInt();
                    genTimestamp = Integer.toUnsignedLong(ts);
                } else if (key == KEY_ALARM_CODE && valueLen >= 4) {
                    ByteBuffer bb = ByteBuffer.wrap(body, valOff, valueLen).order(ByteOrder.LITTLE_ENDIAN);
                    long alarmCode = Integer.toUnsignedLong(bb.getInt()); // bits 0..31
                    long extend = 0L;
                    if (valueLen >= 12) {
                        // skip UTC (4) then read extend (4)
                        bb.position(bb.position() + 4);
                        extend = Integer.toUnsignedLong(bb.getInt());
                    } else if (valueLen >= 8) {
                        // UTC present but no extend
                        // nothing else
                    }

                    // Determine geofence index from bits 4..7
                    for (int b = 4; b <= 7; b++) {
                        if (((alarmCode >>> b) & 1L) == 1L) {
                            fenceIndex = (b - 3); // 4->1, 5->2, 6->3, 7->4
                            break;
                        }
                    }
                    // Determine IN/OUT using extend bits if valid flag set (bit16)
                    if (fenceIndex != null) {
                        boolean validInOut = ((extend >>> 16) & 1L) == 1L;
                        if (validInOut) {
                            int bit = 26 + (fenceIndex - 1); // 26..29
                            long v = (extend >>> bit) & 1L;
                            fenceIn = (v == 1L);
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            i += valueLen;
        }

        // Log a friendly message for geofence alarm if parsed
        if (fenceIndex != null) {
            String dir = fenceIn == null ? "unknown" : (fenceIn ? "in" : "out");
            String timeStr = genTimestamp == null ? Instant.now().toString() : Instant.ofEpochSecond(genTimestamp).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String coords = (lat != null && lon != null) ? String.format("%f,%f", lat, lon) : "unknown";
            System.out.println("[DataCommand] GeoFence alarm " + fenceIndex + " " + dir);
            System.out.println("  Time: " + timeStr);
            System.out.println("  GPS: " + coords);
            if (lat != null && lon != null) {
                System.out.println("  Google Maps: https://www.google.com/maps?q=" + lat + "," + lon);
            }
        }

        // ACK if requested (properties bit4)
        boolean ackRequested = (msg.getProperties() & 0x10) != 0;
        if (ackRequested && ch != null && ch.isActive()) {
            byte[] ackBody = new byte[] { (byte) 0x7F, 0x01, 0x00 };
            byte properties = 0x00; // don't set ACK bit on ACK
            int seq = msg.getSequenceId();
            byte[] frame = FrameUtil.buildFrame(properties, seq, ackBody);
            ch.writeAndFlush(Unpooled.wrappedBuffer(frame));
        }
    }
}

