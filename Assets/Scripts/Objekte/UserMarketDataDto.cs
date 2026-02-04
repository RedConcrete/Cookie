using System;
using System.Collections.Generic;

namespace Server.Data
{
    [Serializable]
    public class UserMarketDataDto
    {
        public UserInformationDto user;
        public List<Market> markets;
    }

    [Serializable]
    public class UserInformationDto
    {
        public string steamId;
        public double cookies;
        public double sugar;
        public double flour;
        public double eggs;
        public double butter;
        public double chocolate;
        public double milk;
    }
}
