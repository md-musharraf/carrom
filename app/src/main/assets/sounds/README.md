# Play style sounds

Drop audio files (`.mp3`, `.ogg`, `.wav`, `.m4a`) into `meme/` or `kids/`, named by event:

| File       | Plays when                          |
|------------|-------------------------------------|
| `pocket`   | a disc is pocketed                  |
| `foul`     | the striker is pocketed             |
| `queen`    | the queen is pocketed               |
| `win`      | you win the match                   |
| `lose`     | you lose the match                  |

e.g. `meme/foul.mp3`. Add `foul_2.mp3`, `foul_3.mp3`, ... and one is picked at random.
A missing file falls back to the normal sound. Keep clips short (under ~5 s; SoundPool limit).
