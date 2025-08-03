package cookie.server.dto;

import cookie.server.enums.ResourceName;

import java.time.LocalDateTime;

public class ResourceDto {
    private ResourceName name;
    private double amount;
    private LocalDateTime version;
}