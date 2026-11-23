# Third-party notices

JAMPlayer application copyright: Copyright © 2026 Jay.W. All rights reserved.

## Radio directories

Directory data is maintained by its respective upstream authors. JAMPlayer normalizes country labels, deduplicates stream URLs and combines entries. Stream availability and broadcaster rights remain with their respective owners. The application does not host the radio audio.

- [72FM / Radio Browser](https://github.com/AlonDrilich/radio-playlists): Playlists CC0 1.0; source MIT. 
- [IPRD](https://github.com/iprd-org/iprd): MIT. 
- [Verified Radio Streams](https://github.com/eyalev/verified-radio-streams): CC0 1.0. 
- [OpenStations](https://github.com/niftyopen/OpenStations): CC BY 4.0. Radio station list from OpenStations by niftyopen, licensed under CC BY 4.0.
- [myMPD WebradioDB](https://github.com/jcorporation/webradiodb): GPL-3.0. myMPD Webradio Database by Juergen Mang / jcorporation, GPL-3.0; retrieved separately from upstream.

The included catalog files retain their provider and attribution metadata. The WebradioDB fallback is fetched separately from upstream when selected; consult its upstream license for that dataset.

## Fonts

DSEG7 Classic by keshikan, SIL Open Font License 1.1. The complete font license is included in each edition at `Source/ui/fonts/DSEG-LICENSE.txt`.

## Dependencies and build tools

- AndroidX Media3 and AndroidX Annotation: Apache License 2.0, https://github.com/androidx/media
- Gradle Wrapper: Apache License 2.0, https://github.com/gradle/gradle
- Android Gradle Plugin: Apache License 2.0, https://android.googlesource.com/platform/tools/base/

Android dependencies are resolved through the official Gradle repositories; application APKs are built using these dependencies. Their licenses and applicable notices remain with the respective projects.
