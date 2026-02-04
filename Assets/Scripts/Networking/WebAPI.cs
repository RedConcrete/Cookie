using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;
using UnityEngine;
using UnityEngine.Networking;
using Newtonsoft.Json;
using Server.Data;
using UnityEngine.SceneManagement;
using Steamworks;
using Unity.VisualScripting;
using UnityEngine.SocialPlatforms;
using UnityEditor.PackageManager;
using System.Security.Cryptography;
using System.Net.WebSockets;
using System.Threading;
using System.Threading.Tasks;


public class WebAPI : MonoBehaviour
{
    public static WebAPI Instance { get; private set; }
    public static User user;  // Statische Variable f�r den Player
    public static ulong SteamId;  // Statische Steam-ID des Players
    private AuthTicket authTicket;

    List<Market> marketList;
    //private string baseUrl = "http://cookie-server.r3dconcrete.de:3000";
    private string baseUrl = "http://localhost:8080";
    private GameManager gameManager;
    private int loginScene = 0;
    private int loginLoadTime = 5;

    private void Awake()
    {
        try
        {
            Steamworks.SteamClient.Init(2816100);

            StartCoroutine(AuthenticateUser());
            ConnectToWebSocket();
        }
        catch (System.Exception e)
        {
            Debug.LogError(e + " Steam connection ERROR! ");
#if UNITY_EDITOR
            UnityEditor.EditorApplication.isPlaying = false;
#endif
            Application.Quit();
        }

        if (Instance != null)
        {
            Debug.Log("Destroying duplicate WebAPI instance.");
            Destroy(gameObject);
        }
        else
        {
            Instance = this;
            DontDestroyOnLoad(gameObject);
        }
    }

    private void Update()
    {
        Steamworks.SteamClient.RunCallbacks();

        if (gameManager == null && SceneManager.GetActiveScene().buildIndex != loginScene)
        {
            gameManager = GameObject.Find("GameManager").GetComponent<GameManager>();
        }
    }

    private void OnApplicationQuit()
    {
        if (authTicket != null)
        {
            Steamworks.SteamUser.EndAuthSession(Steamworks.SteamClient.SteamId);
        }
        Steamworks.SteamClient.Shutdown();
    }

    private IEnumerator AuthenticateUser()
    {
        // Holen eines Authentifizierungstickets
        authTicket = Steamworks.SteamUser.GetAuthSessionTicket();

        if (authTicket != null)
        {
            Debug.Log("Successfully created authentication ticket.");
            byte[] ticketData = authTicket.Data;
            string base64Ticket = Convert.ToBase64String(ticketData);

            Debug.Log("SteamID: " + GetSteamID());

            // Warten für 1 Sekunde
            yield return new WaitForSeconds(loginLoadTime);
            StartCoroutine(WebAPI.Instance.GetPlayer(SteamId.ToString(), true));
        }
        else
        {
            Debug.LogError("Failed to create authentication ticket.");
        }

        yield return null;
    }

    public IEnumerator PostPlayer()
    {
        if (authTicket != null)
        {
            string url = $"{baseUrl}/users/{SteamId}";

            UnityWebRequest webRequest = new UnityWebRequest(url, "POST");
            webRequest.downloadHandler = new DownloadHandlerBuffer();
            webRequest.SetRequestHeader("Content-Type", "application/json");

            yield return webRequest.SendWebRequest();

            if (webRequest.result != UnityWebRequest.Result.Success)
            {
                Debug.LogError($"Error: {webRequest.error}");
                Debug.LogError($"Response Code: {webRequest.responseCode}");
                Debug.LogError($"Response: {webRequest.downloadHandler.text}");
            }
            else
            {
                string playerJsonData = webRequest.downloadHandler.text;
                if (!string.IsNullOrEmpty(playerJsonData))
                {
                    user = JsonConvert.DeserializeObject<User>(playerJsonData);  // Zuweisung zur statischen Variable
                    SceneManager.LoadScene(1);
                }
                else
                {
                    Debug.LogError("Received empty response from the server");
                }
            }
            webRequest.Dispose();
        }
        else
        {
            Debug.LogError("No Steam connection");
        }
    }

    public IEnumerator GetPlayer(string steamid, bool isLoggingIn)
    {
        if (authTicket != null)
        {
            string url = $"{baseUrl}/users/{steamid}";

            using (UnityWebRequest webRequest = UnityWebRequest.Get(url))
            {
                yield return webRequest.SendWebRequest();
                switch (webRequest.result)
                {
                    case UnityWebRequest.Result.ConnectionError:
                        Debug.LogError(String.Format("ERROR " + webRequest.error));
#if UNITY_EDITOR
                        UnityEditor.EditorApplication.isPlaying = false;
#endif
                        Application.Quit();
                        break;
                    case UnityWebRequest.Result.ProtocolError:
                        Debug.LogError(String.Format("ERROR " + webRequest.error));

                        break;
                    case UnityWebRequest.Result.DataProcessingError:
                        Debug.LogError(String.Format("ERROR " + webRequest.error));

                        break;
                    case UnityWebRequest.Result.Success:
                        string playerJsonData = webRequest.downloadHandler.text;
                        user = JsonConvert.DeserializeObject<User>(playerJsonData);  // Zuweisung zur statischen Variable
                        if (isLoggingIn)
                        {
                            SceneManager.LoadScene(1);
                            Debug.Log("Login successful with: " + steamid);
                        }
                        break;
                }
            }
        }
        else
        {
            Debug.LogError("No Steam connection");
        }
    }

    /**
     *
     * 
    **/
    private ClientWebSocket webSocket;
    private CancellationTokenSource cancellationTokenSource;

    private async void ConnectToWebSocket()
    {
        try
        {
            webSocket = new ClientWebSocket();
            Uri serverUri = new Uri("ws://localhost:8080/ws-market");
            cancellationTokenSource = new CancellationTokenSource();
            await webSocket.ConnectAsync(serverUri, cancellationTokenSource.Token);
            Debug.Log("Connected to WebSocket");
            ReceiveWebSocketMessages();
        }
        catch (Exception e)
        {
            Debug.LogError("WebSocket connection error: " + e.Message);
        }
    }

    private async void ReceiveWebSocketMessages()
    {
        var buffer = new byte[1024 * 4];
        while (webSocket.State == WebSocketState.Open)
        {
            try
            {
                var result = await webSocket.ReceiveAsync(new ArraySegment<byte>(buffer), cancellationTokenSource.Token);
                if (result.MessageType == WebSocketMessageType.Text)
                {
                    string message = Encoding.UTF8.GetString(buffer, 0, result.Count);
                    List<Market> newMarketList = JsonConvert.DeserializeObject<List<Market>>(message);
                    
                    // Dispatch to main thread if needed
                    UnityMainThreadDispatcher.Instance().Enqueue(() => {
                         marketList = newMarketList;
                         gameManager.UpdateMarketDataAndUserData(); // Or specific update method
                    });
                }
                else if (result.MessageType == WebSocketMessageType.Close)
                {
                    await webSocket.CloseAsync(WebSocketCloseStatus.NormalClosure, string.Empty, CancellationToken.None);
                }
            }
            catch (Exception e)
            {
                Debug.LogError("WebSocket receive error: " + e.Message);
                break;
            }
        }
    }

    public IEnumerator UpdatePlayerAndMarket(string steamid, int amount)
    {
        string url = baseUrl + "/api/v1/game/init/" + steamid + "?marketHistoryAmount=" + amount;

        using (UnityWebRequest webRequest = UnityWebRequest.Get(url))
        {
            yield return webRequest.SendWebRequest();
            if (webRequest.result == UnityWebRequest.Result.Success)
            {
                string json = webRequest.downloadHandler.text;
                UserMarketDataDto data = JsonConvert.DeserializeObject<UserMarketDataDto>(json);
               
                // Map User (existing logic)
                user = new User 
                { 
                    steamid = data.user.steamId,
                    cookies = (int)data.user.cookies, 
                    sugar = (int)data.user.sugar,
                    flour = (int)data.user.flour,
                    eggs = (int)data.user.eggs,
                    butter = (int)data.user.butter,
                    chocolate = (int)data.user.chocolate,
                    milk = (int)data.user.milk
                };

                 // Map Market
                marketList = data.markets; // Assuming Market class matches MarketDto
            }
            else 
            {
                Debug.LogError("Error updating player and market: " + webRequest.error);
            }
        }
    }


    public IEnumerator GetPrices(int amount)
    {
        string url = baseUrl + "/markets/" + amount;

        using (UnityWebRequest webRequest = UnityWebRequest.Get(url))
        {
            yield return webRequest.SendWebRequest();
            switch (webRequest.result)
            {
                case UnityWebRequest.Result.ConnectionError:
                case UnityWebRequest.Result.DataProcessingError:
                    Debug.LogError(String.Format("ERROR", webRequest.error));
                    break;
                case UnityWebRequest.Result.Success:
                    string marketJsonData = webRequest.downloadHandler.text;
                    marketList = null;
                    marketList = JsonConvert.DeserializeObject<List<Market>>(marketJsonData);
                    break;
            }
        }
    }

    public IEnumerator PostBuy(string steamid, string rec, int amount)
    {
        MarketRequest marketRequest = new MarketRequest(steamid, rec, amount);
        return DoMarketAction("buy", marketRequest);
    }

    public IEnumerator PostSell(string steamid, string rec, int amount)
    {
        MarketRequest marketRequest = new MarketRequest(steamid, rec, amount);
        return DoMarketAction("sell", marketRequest);
    }

    private IEnumerator DoMarketAction(string action, MarketRequest marketRequest)
    {
        {
            string url = baseUrl + "/" + action;
            string json = JsonUtility.ToJson(marketRequest);

            using (UnityWebRequest webRequest = new UnityWebRequest(url, "POST"))
            {
                byte[] bodyRaw = Encoding.UTF8.GetBytes(json);
                webRequest.uploadHandler = new UploadHandlerRaw(bodyRaw);
                webRequest.downloadHandler = new DownloadHandlerBuffer();
                webRequest.SetRequestHeader("Content-Type", "application/json");

                yield return webRequest.SendWebRequest();

                switch (webRequest.result)
                {
                    case UnityWebRequest.Result.ConnectionError:
                    case UnityWebRequest.Result.DataProcessingError:
                        Debug.LogError($"ERROR: {webRequest.error}");
                        break;
                    case UnityWebRequest.Result.ProtocolError:
                        Debug.LogError($"HTTP Error: {webRequest.error}");
                        break;
                    case UnityWebRequest.Result.Success:
                        Debug.Log("Received: " + webRequest.downloadHandler.text);
                        string playerJsonData = webRequest.downloadHandler.text;
                        if (string.IsNullOrEmpty(playerJsonData))
                        {
                            Debug.LogError("Received empty response from the server");
                        }
                        else
                        {
                            Debug.Log(playerJsonData);
                            JsonUtility.FromJsonOverwrite(playerJsonData, user);
                            gameManager.UpdateRecources();
                        }
                        break;
                }
                Debug.Log("Player has: " + user.cookies + " Cookies");
                gameManager.UpdateMarketDataAndUserData();
            }
        }
    }

    public List<Market> GetMarket()
    {
        return marketList;
    }

    public ulong GetSteamID()
    {
        SteamId = Steamworks.SteamClient.SteamId;
        return SteamId;
    }

}
