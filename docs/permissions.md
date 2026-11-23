# Permissions

Roomtone asks for a few permissions. Two of them are needed for it to work at
all; the others are optional or automatic.

| Permission | Why Roomtone needs it | If you don't grant it |
|---|---|---|
| Notification access | To read what Shazam recognizes. Roomtone only reads Shazam's notifications and ignores every other one. | Roomtone can't see any track. Nothing shows up. |
| Display over other apps | To start Auto Shazam for you from the background. | Auto Shazam never starts by itself. You have to start it in Shazam. |
| Notifications (Android 13+) | To tell you when a new version is out. | No update notice. The in-app update button still works. |
| Install apps | Only for updates you start from Roomtone. Android asks the first time. Not used when Obtainium installed Roomtone. | You can't update from inside Roomtone. Install the APK yourself. |
| Battery optimization (optional, recommended) | Keeps Android from stopping Roomtone in the background. | Android may stop Roomtone after a while. See [Troubleshooting](troubleshooting.md). |
| Device admin: lock the screen (optional) | Only if you turn on "Turn off the screen in standby": locks the phone after the standby time, like the power button. Roomtone can do nothing else as device admin. | The screen turns off with your phone's screen timeout instead. |
| Internet and network state | To talk to Last.fm, Discogs and the other services, and to know when to send queued scrobbles. Automatic, nothing to grant. | - |

Roomtone never uses the microphone. See [Privacy](privacy.md).

## How to grant them

Open Roomtone. On a fresh install the main screen shows a card with both
required permissions and a button for each, which opens the right Android
screen. The card disappears once both are granted.

You can also open **Settings → Permissions** in the app at any time. Each
permission has a button there, with a line explaining why it is needed.
