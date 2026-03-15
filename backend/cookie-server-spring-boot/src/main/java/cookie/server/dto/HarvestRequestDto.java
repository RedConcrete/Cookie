package cookie.server.dto;

import cookie.server.enums.ResourceName;

public class HarvestRequestDto {

    private ResourceName resource;

    public ResourceName getResource() {
        return resource;
    }

    public void setResource(ResourceName resource) {
        this.resource = resource;
    }
}
