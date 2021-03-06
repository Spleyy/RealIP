package net.tcpshield.tcpshield;

import net.tcpshield.tcpshield.abstraction.IPacket;
import net.tcpshield.tcpshield.abstraction.IPlayer;
import net.tcpshield.tcpshield.abstraction.TCPShieldConfig;
import net.tcpshield.tcpshield.exception.*;
import net.tcpshield.tcpshield.validation.IPValidation;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class HandshakePacketHandler {

    private final Logger logger;
    private final IPValidation ipValidation;
    private final TCPShieldConfig config;

    public HandshakePacketHandler(Logger logger, TCPShieldConfig config) {
        try {
            this.logger = logger;
            this.ipValidation = new IPValidation(config, logger);
            this.config = config;
        } catch (Exception e) {
            throw new TCPShieldInitializationException(e);
        }
    }

    public void onHandshake(IPacket packet, IPlayer player) {
        String rawPayload = packet.getRawPayload();

        try {
            InetAddress inetAddress = InetAddress.getByName(player.getIP());
            if (!ipValidation.validateIP(inetAddress)) throw new InvalidIPException();

            if (rawPayload == null)
                return; // raw payload is passed when it's not available -> e.g. Paper server list ping events

            String extraData = null;

            // fix for e.g. incoming FML tagged packets
            String cleanedPayload;
            int nullIndex = rawPayload.indexOf('\0');
            if (nullIndex != -1) { // FML tagged
                cleanedPayload = rawPayload.substring(0, nullIndex);
                extraData = rawPayload.substring(nullIndex);
            } else { // standard
                cleanedPayload = rawPayload;
            }

            String[] payload = cleanedPayload.split("///", 4);
            if (payload.length < 2)
                throw new MalformedPayloadException("payload.length < 2. Raw payload = \"" + rawPayload + "\"");

            String hostname = payload[0];
            String ipData = payload[1];

            String[] hostnameParts = ipData.split(":");
            String host = hostnameParts[0];
            int port = Integer.parseInt(hostnameParts[1]);

            InetSocketAddress newIP = new InetSocketAddress(host, port);
            player.setIP(newIP);

            if (extraData != null) hostname = hostname + extraData;

            packet.modifyOriginalPacket(hostname);
        } catch (InvalidIPException e) {
            handleInvalidIPException(player, rawPayload);
        } catch (ConnectionNotProxiedException e) {
            handleNotProxiedConnection(player, rawPayload);
        } catch (IPModificationFailureException e) {
            this.logger.warning(String.format("%s[%s/%s]'s IP failed to be modified. Raw payload = \"%s\"", player.getName(), player.getUUID(), player.getIP(), rawPayload));
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleInvalidIPException(IPlayer player, String rawPayload) {
        if (config.isDebug()) {
            this.logger.warning(String.format("%s[%s/%s] failed the ip check. Raw payload = \"%s\"", player.getName(), player.getUUID(), player.getIP(), rawPayload));
        }

        if (config.isOnlyProxy()) {
            player.disconnect();
        }
    }

    private void handleNotProxiedConnection(IPlayer player, String rawPayload) {
        if (!config.isOnlyProxy()) return;

        if (config.isDebug()) {
            this.logger.info(String.format("%s[%s/%s] was disconnected because no proxy info was received and only-allow-proxy-connections is enabled. Raw payload = \"%s\"", player.getName(), player.getUUID(), player.getIP(), rawPayload));
        }

        player.disconnect();
    }
}
