# TODO — Not Yet Implemented Requirements

Requirements documented in `architecture.md` that are not yet implemented in code.

---

## Mini Player

- [ ] **Mini Player tap → Radio tab:** Tapping the Mini Player while Radio is the active source should navigate to the Radio tab. Currently the click handler in [PlayerScreen.kt L234-242](file:///home/michael/develop/Simple_music_app/app/src/main/java/com/michael/simplemusic/ui/PlayerScreen.kt#L234-L242) has no `ChannelType.RADIO` branch — tapping does nothing for Radio.

## Podcast Module

- [ ] **Podcast playback speed:** No playback speed control exists (0.5x–2x). Common podcast UX expectation.

## Widget

- [ ] **Widget progress indicator:** Architecture says the Widget provides playback controls and metadata. Currently implemented with Play/Pause, Rewind, Fast Forward, and title/subtitle — but no progress bar or duration info.
- [ ] **Widget metadata sync:** Widget updates are sent via broadcast from `MusicService`, but there's no periodic refresh mechanism. Widget may show stale metadata after track transitions if the broadcast is missed.
