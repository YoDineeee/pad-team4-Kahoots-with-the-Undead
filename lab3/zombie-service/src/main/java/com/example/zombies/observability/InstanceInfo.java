package com.example.zombies.observability;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Who am I? Several copies (instances) of this service can run at the same time,
 * so each one needs its own id and its own address.
 *
 *   id      = INSTANCE_ID if set, otherwise "<service>-<hostname>".
 *             In Docker the hostname is the container id, so every replica gets a different id.
 *   address = this container's IP address (other containers reach us there).
 */
@Component
public class InstanceInfo {

    private final String serviceName;
    private final String instanceId;
    private final String ipAddress;

    public InstanceInfo(@Value("${spring.application.name}") String serviceName,
                        @Value("${INSTANCE_ID:}") String instanceId) throws UnknownHostException {
        InetAddress me = InetAddress.getLocalHost();
        this.serviceName = serviceName;
        this.instanceId = instanceId.isBlank() ? serviceName + "-" + me.getHostName() : instanceId;
        this.ipAddress = me.getHostAddress();
    }

    public String serviceName() { return serviceName; }
    public String instanceId() { return instanceId; }
    public String ipAddress() { return ipAddress; }
}
