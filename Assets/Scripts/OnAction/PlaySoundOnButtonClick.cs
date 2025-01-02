using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;

public class PlaySoundOnButtonClick : MonoBehaviour
{

    private void Start()
    {
        // Alle Buttons im aktuellen Canvas finden
        Button[] buttons = FindObjectsOfType<Button>();

        foreach (Button button in buttons)
        {
            // Click-Sound hinzuf�gen
            button.onClick.AddListener(() => PlayClickedSound());

            // Hover-Sound hinzuf�gen
            EventTrigger trigger = button.gameObject.AddComponent<EventTrigger>();
            EventTrigger.Entry entry = new EventTrigger.Entry
            {
                eventID = EventTriggerType.PointerEnter
            };
            entry.callback.AddListener((eventData) => PlayHoveredSound());
            trigger.triggers.Add(entry);
        }
    }

    private void PlayClickedSound()
    {
        SoundManager.Instance.PlaySound("ButtonClick");
    }

    private void PlayHoveredSound()
    {
        int num = Random.Range(2, 4);
        SoundManager.Instance.PlaySound("ButtonHover" + num);
        
    }
}
