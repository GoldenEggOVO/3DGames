# Kenney audio sources

Selected original OGG files from Kenney, licensed under CC0 1.0.
The original license notices are retained alongside these sources and included in the generated pack.

- Casino Audio 1.1: https://kenney.nl/assets/casino-audio
- Interface Sounds 1.0: https://kenney.nl/assets/interface-sounds
- Impact Sounds 1.0: https://kenney.nl/assets/impact-sounds
- License: https://creativecommons.org/publicdomain/zero/1.0/

`originals/shared/sounds/sound_events.json` maps shared gameplay events to their original source filenames.
The processed physical-action samples use mono positional audio; Mahjong samples retain their original channel layouts. The `physical_audio` section of `sources.json` records the exact archive members, trimming, high-pass filtering, fades and peak normalization for physical action samples.
Minecraft chooses one variation per event; variations never play simultaneously.
Mahjong also uses these CC0 recordings, with exact mappings in the `mahjong_materials` section of `sources.json`. The user-provided card play recording retains its own source notice.
