# ChirpMeIn

The ChirpMeIn Android application is a prototype of the ChirpMeIn Frequency-Modulated Continuos Wave based two factor authentication application. 

## Building the Application

To build this application, simply open it in Android Studio and run the primary build configuration.

## Using the Application

In order to run this application as is, a Google account is needed, as ChirpMeIn relies on a serverless backend implementation using Google Firebase.
The application will not work correctly unless you are directly added as a developer, as Google firebase services will refuse any requests
from the application unless you are a verified user. If you are interested in joining the development of ChirpMeIn, you can send me your 
SHA-256 signing report from Android Studio by running 

```
gradle signingreport
```

from within Android studio, and I can add you as a developer. You can find the full documentation for this project in the javadoc folder
