# MuziFind - Recognize Reel Songs with Internal Audio Analysis

<a href="https://render.com"><img alt="Production" src="https://img.shields.io/badge/production-up-lgreen.svg"/></a>
[![Maintainability](https://api.codeclimate.com/v1/badges/ff1d96175429d4e716d3/maintainability)](https://codeclimate.com/github/mssandeepkamath/muparse-android/maintainability)
[![Test Coverage](https://api.codeclimate.com/v1/badges/ff1d96175429d4e716d3/test_coverage)](https://codeclimate.com/github/mssandeepkamath/muparse-android/test_coverage)

MuziFind is an Android app that captures and analyzes internal audio from various applications, such as Instagram reels and YouTube shorts. It effectively identifies the audio being played and offers convenient links to Spotify, YouTube, and the web for further exploration.

<a href="https://play.google.com/store/apps/details?id=com.sandeep.music_recognizer_app&hl=en&gl=US">
    <img alt="Play Store"
        height="80"
        src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" />
        </a>

## Table of Contents

- [Demo Video](#demo-video)
- [Architecture Overview](#architecture-overview)
- [Getting Started](#getting-started)
- [Contribution](#contribution)
- [License](#license)
- [Contact](#contact)

## Demo Video

<a href="https://www.youtube.com/watch?v=nNGeyQrl9QY">
    <img alt="Play Store"
        height="50"
        src="https://img.shields.io/badge/YouTube-%23FF0000.svg?style=for-the-badge&logo=YouTube&logoColor=white" />
        </a>

## Architecture Overview

![Architecture](https://github.com/mssandeepkamath/muparse-android/assets/90695071/d3c4feb4-2586-4a4d-b521-6810f61659b3)

## Getting Started

1. **Firebase Setup:** Configure your Firebase app and place the `google-services.json` file at the root directory.
2. **Firebase Realtime Database:** Create a Firebase Realtime Database to store necessary data, as shown in the architecture overview.

```json
{
  "secret_keys": {
    "access_key": "your_access_key",
    "host": "identify-ap-your_location",
    "secret_key": "your_secret_key"
  }
}
```

3. **ACR Cloud Integration:** Sign up for ACR Cloud to obtain secret keys, access keys, and bucket information for audio recognition.

## Contribution

Contributions to MuziFind are welcome! To contribute:

1. Fork this repository.
2. Create a new branch for your feature/fix.
3. Make your changes and commit with descriptive messages.
4. Create a pull request outlining your changes.

## License

This project is licensed under the [MIT License](LICENSE).

## Contact

For questions or feedback, feel free to [email us](mailto:msandeepcip@gmail.com).

---

**Disclaimer:** MuziFind is an independent project and not affiliated with the mentioned platforms (Instagram, YouTube, Spotify). It is designed for audio recognition purposes.
