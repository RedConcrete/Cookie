using System;

namespace Server.Data
{
    [Serializable]
    public class MarketRequest
    {
        public string userId;
        public string action;
        public ResourceDto resource;

        public MarketRequest(string userId, string action, string resourceName, int amount)
        {
            this.userId = userId;
            this.action = action;
            this.resource = new ResourceDto
            {
                name = resourceName.ToUpper(),
                amount = amount
            };
        }
    }

    [Serializable]
    public class ResourceDto
    {
        public string name;
        public double amount;
    }
}
