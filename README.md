<p align="center">
  <img src="assets/banner.svg" width="100%" alt="SmartLamp, spelled out in glowing LED dots beside a small smiling LED-matrix lamp that bounces, blinks and turns its face into a beating heart">
</p>

<p align="center">
  <b>She lives on a breadboard, inside a cardboard box, and she has never once had a bad day.</b>
</p>

<p align="center">
  <code>Kotlin</code>&nbsp; <code>Android 13+</code>&nbsp; <code>ESP32</code>&nbsp; <code>64 LEDs</code>&nbsp; <code>HTTP</code>&nbsp; <code>UDP</code>&nbsp; <code>one cardboard box</code>
</p>

SmartLamp is an Android app for a very small lamp with a very large personality. Spin the colour wheel and she wears it. Pick a mood and she commits to it completely. Play a song on your phone and she dances, because the app listens to what the phone is playing and passes the rhythm along over Wi-Fi.

*Lampă* is feminine in Romanian, so around here the lamp is a she. I don't make the rules.

<p align="center">
  <img src="assets/divider.svg" width="100%" alt="">
</p>

## Say hello

<p align="center">
  <img src="assets/demo.gif" width="560" alt="A phone running the SmartLamp app changes animations and colours on an 8 by 8 LED matrix wired to an ESP32 on a breadboard">
  <br>
  <sub>The real thing at double speed, and yes, that is the real box. <a href="Smart_Lamp.mp4">Watch the full clip.</a></sub>
</p>

## Fourteen moods

She has one for every occasion, including several occasions that don't exist yet.

<p align="center">
  <img src="assets/moods.svg" width="100%" alt="Fourteen small LED panels, each playing one animation: Spiral, Fire, Rain, Heart, Plasma, Noise, Snake, Twinkle, Ripple, Meteor, ColorWave, Pacifica, Matrix and Pulse">
  <br>
  <sub>Little portraits drawn for this page. The originals live on the lamp, and she does them her own way.</sub>
</p>

## Five ways to spend an evening

| Mode | What she does |
|---|---|
| **Ambient** | Keeps the room company and asks for nothing |
| **Run All Animations** | Goes through every mood, because choosing is hard |
| **Pick Animation** | Commits to the one you picked, for as long as you like |
| **Pick Color** | Wears anything on the colour wheel |
| **Music Visualizer** | Stops being furniture |

And the small things that make her easy to live with:

- **A brightness slider** that goes from 0 to 255, from barely awake to showing off.
- **A bedtime button in your notifications.** Tap *Turn off* without opening the app.
- **She keeps you posted.** The app asks how she's doing every three seconds, so its screen stays in step with the lamp.
- **Controls that know when to step back.** The ones you can't use right now fade and shrink out of the way.

<p align="center">
  <img src="assets/divider.svg" width="100%" alt="">
</p>

## How she hears music

She has no ears. She has something better: a phone that tells her everything.

<p align="center">
  <img src="assets/music.svg" width="100%" alt="A phone playing music sends a stream of three-byte packets, holding volume, beat and hue, over UDP port 4210 to the lamp, which dances">
</p>

1. **The phone listens to itself.** Android's playback capture picks up media audio at 44.1 kHz, so the room can be as noisy as it wants.
2. **Loudness becomes volume.** Each slice of sound is measured and squeezed into a number from 0 to 255.
3. **A sudden jump becomes a beat.** If a slice is 40% louder than the recent average, that's her cue.
4. **The sound picks a colour.** The app compares the energy in the two halves of each slice and turns the result into a hue.
5. **Three bytes fly across the room.** Dozens of tiny packets a second, sent over UDP. If one gets lost, nobody stops to look for it. The next one is already on its way, and the beat goes on.

## Wake her up

You'll need Android Studio, a phone on Android 13 or newer, and a lamp on the same Wi-Fi.

```bash
git clone https://github.com/CiobanicaCrenguta/SmartLamp.git
cd SmartLamp
./gradlew installDebug
```

1. Open the app and type the lamp's IP address. She shows it on her little LCD.
2. Tap **Connect** and say hello.
3. For music, first allow **Microphone** for Smart_Lamp in Android's app settings. The app needs it for audio capture and doesn't ask for it yet.
4. Choose **Music Visualizer**, accept the capture prompt, and play something you love.

<details>
<summary><b>What she's made of</b></summary>
<br>

- an ESP32 development board, doing all the thinking
- an 8 by 8 RGB LED matrix, doing all the feeling
- a character LCD, for introductions
- a four-cell battery pack and a small power board
- one breadboard and more jumper wires than strictly necessary
- one cardboard box, load-bearing

</details>

<details>
<summary><b>How to talk to her</b></summary>
<br>

This is everything the app expects from the lamp.

| Request | Meaning |
|---|---|
| `GET /` | How are you? Answers with JSON: `{"mode": 0, "brightness": 255, "auto": 0}` |
| `GET /setMode?val=0` | Plain light |
| `GET /setMode?val=1` | Animation |
| `GET /setAuto?val=1` | Run through every animation (`0` stays on one) |
| `GET /setAnimation?val=0` | Choose a mood, from `0` (Spiral) to `13` (Pulse), in the order shown above |
| `GET /setColor?r=255&g=143&b=191` | Wear this colour |
| `GET /setBrightness?val=128` | Brightness from `0` to `255` |

In the status reply, `mode` is `2` while she's dancing. Music arrives on **UDP port 4210**, three bytes per packet:

| Byte | Holds |
|---|---|
| 0 | Volume, `0` to `255` |
| 1 | Beat, `0` or `1` |
| 2 | Hue |

</details>

<details>
<summary><b>Where everything lives</b></summary>
<br>

```text
app/src/main/java/com/example/smart_lamp/
├── ConnectionActivity.kt     the front door: type her IP address
├── MainActivity.kt           colours, moods, brightness and status checks
├── NotificationReceiver.kt   the bedtime button
├── VisualizerActivity.kt     asks permission to listen
├── VisualizerService.kt      keeps the music going in the background
├── AudioCaptureManager.kt    finds the volume, the beat and the colour
└── VisualizerSender.kt       throws three bytes across the room
```

</details>

## Her wishlist

She is very trusting. Anyone on her Wi-Fi can talk to her, with no password and no encryption, so keep her on a network you trust. She'd like that to change, along with a few other things:

- [ ] the ESP32 firmware, published here next to the app
- [ ] a secret handshake, so she only listens to her own phone
- [ ] finding her on the network without typing an IP address
- [ ] the app asking for audio permission by itself
- [ ] a real enclosure, although she is very attached to the box

<p align="center">
  <img src="assets/divider.svg" width="100%" alt="">
</p>

<p align="center">
  <sub>Made by <a href="https://github.com/CiobanicaCrenguta">Crenguța</a>. Be kind to your lamps.</sub>
</p>
