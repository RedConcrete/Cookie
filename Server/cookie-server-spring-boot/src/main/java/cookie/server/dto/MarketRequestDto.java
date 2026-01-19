package cookie.server.dto;

import cookie.server.enums.MarketAction;

public class MarketRequestDto {
    private String userId;
    private MarketAction action;
    private ResourceDto resource;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public MarketAction getAction() {
        return action;
    }

    public void setAction(MarketAction action) {
        this.action = action;
    }

    public ResourceDto getResource() {
        return resource;
    }

    public void setResource(ResourceDto resource) {
        this.resource = resource;
    }
}
