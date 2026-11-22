# Install and updates

## With Obtainium (recommended)

[Obtainium](https://github.com/ImranR98/Obtainium) installs apps straight
from their GitHub releases and keeps them up to date. Install Obtainium, then
open this link on the phone:
[add Roomtone to Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/DerefDev/roomtone).
Leave the options as they are and tap **Install**.

The first time, Google Play Protect may say it has never scanned Roomtone and
offer to scan it: let it scan, then the installation goes ahead.

Obtainium checks for new versions and installs them. Roomtone notices that
Obtainium installed it and leaves updates to it: it doesn't check GitHub
itself, and Settings → About shows "Updates are managed by Obtainium" with a
button to open it. The same happens with F-Droid clients.

## From the APK

Download the APK from the
[latest release](https://github.com/DerefDev/roomtone/releases/latest) and
open it on the phone. If Play Protect blocks it, see
[Play Protect blocks the installation](troubleshooting.md#play-protect-blocks-the-installation).

On first start Roomtone asks whether it may check GitHub once a day for a new
version. Nothing is checked until you say yes, and you can change it in
Settings → About, where **Check for updates** also checks right away.

When a new version is out, Roomtone tells you once with a notification and a
message when you open it, then keeps a banner at the top of the menu. Tap
**Update**: Roomtone downloads the APK from the GitHub release, checks that
it is the published file, and Android asks you to confirm. The first time,
Android also asks you to allow Roomtone to install apps.

## After an update

Your settings, logins, drafts and scrobbles are kept. The first time you open
the new version, Roomtone shows what's new.

Updates only install over a Roomtone signed with the same key, so a version
you built yourself can't be updated with the APK from GitHub, and the other
way round.
