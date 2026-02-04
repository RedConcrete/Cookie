using System;
using UnityEngine;
using UnityEngine.UIElements;
using UnityEngine.SceneManagement;
using System.Threading.Tasks;
using System.Collections;
using System.Collections.Generic;
using System.Linq;
using Server.Data; // Ensure this is imported for User/Market classes

public class GameManager : MonoBehaviour
{
    [Header("UI Toolkit")]
    public UIDocument uiDocument;
    private Label cookieLabel;
    private Label sugarLabel;
    private Label flourLabel;
    private Label eggsLabel;
    private Label butterLabel;
    private Label chocolateLabel;
    private Label milkLabel;
    private Label updateTimeLabel;
    private Label playerIdLabel;
    private TextField amountInput;
    private Button buyButton;
    private Button sellButton;

    [Header("Timer:")]
    public int updateTime = 11;
    public float timeRemaining = -1;
    public bool timerIsRunning = false;
    
    // Internal State
    private User currentUser;
    private List<Market> marketList;
    private string selectedResource = "sugar"; // Default selection

    // Singleton or direct reference for simplicity in this refactor
    private void OnEnable()
    {
        if (uiDocument == null) uiDocument = GetComponent<UIDocument>();
        if (uiDocument != null)
        {
            var root = uiDocument.rootVisualElement;

            cookieLabel = root.Q<Label>("cookie-value");
            sugarLabel = root.Q<Label>("sugar-value");
            flourLabel = root.Q<Label>("flour-value");
            eggsLabel = root.Q<Label>("eggs-value");
            butterLabel = root.Q<Label>("butter-value");
            chocolateLabel = root.Q<Label>("chocolate-value");
            milkLabel = root.Q<Label>("milk-value");
            updateTimeLabel = root.Q<Label>("update-time");
            playerIdLabel = root.Q<Label>("player-id");
            
            amountInput = root.Q<TextField>("amount-input");
            buyButton = root.Q<Button>("buy-btn");
            sellButton = root.Q<Button>("sell-btn");

            if (buyButton != null) buyButton.clicked += Buy;
            if (sellButton != null) sellButton.clicked += Sell;
        }
    }

    private void Start()
    {
        currentUser = WebAPI.user;
        UpdateRecources();
    }

    private void Update()
    {
        if (timerIsRunning)
        {
            timeRemaining -= Time.deltaTime;
            DisplayTime(timeRemaining);

            if (timeRemaining <= 1)
            {
                timeRemaining = updateTime;
                UpdateMarketDataAndUserData();
            }
        }
    }

    public void UpdateMarketDataAndUserData()
    {
        if(currentUser != null)
             StartCoroutine(WebAPI.Instance.UpdatePlayerAndMarket(currentUser.steamid, 20));
    }

    public void UpdateRecources()
    {
        if (currentUser == null) return;

        UpdateLabel(cookieLabel, currentUser.cookies);
        UpdateLabel(sugarLabel, currentUser.sugar);
        UpdateLabel(flourLabel, currentUser.flour);
        UpdateLabel(eggsLabel, currentUser.eggs);
        UpdateLabel(butterLabel, currentUser.butter);
        UpdateLabel(chocolateLabel, currentUser.chocolate);
        UpdateLabel(milkLabel, currentUser.milk);
        
        if(playerIdLabel != null) playerIdLabel.text = "SteamID: " + currentUser.steamid;
    }

    private void UpdateLabel(Label label, double value)
    {
        if (label != null) label.text = value.ToString("F0");
    }

    void DisplayTime(float timeToDisplay)
    {
        if (timeToDisplay < 0) timeToDisplay = 0;
        float minutes = Mathf.FloorToInt(timeToDisplay / 60); 
        float seconds = Mathf.FloorToInt(timeToDisplay % 60);
        if(updateTimeLabel != null) updateTimeLabel.text = string.Format("{0:00}:{1:00}", minutes, seconds);
    }

    public void Buy()
    {
        int amount = 1;
        if(amountInput != null) int.TryParse(amountInput.value, out amount);
        
        StartCoroutine(WebAPI.Instance.PostBuy(currentUser.steamid, selectedResource, amount));
    }

    public void Sell()
    {
        int amount = 1;
        if(amountInput != null) int.TryParse(amountInput.value, out amount);
        
        StartCoroutine(WebAPI.Instance.PostSell(currentUser.steamid, selectedResource, amount));
    }
}
