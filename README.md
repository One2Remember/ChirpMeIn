# ChirpMeIn

The ChirpMeIn Android application is a prototype of the ChirpMeIn Frequency-Modulated Continuous Wave based two factor authentication application. To find the web application which serves as a proof-of-concept test application for this protocol, please visit my [partner's repository](https://github.com/carlguo2/ChirpMeInWeb). 

## Building the Application

To build this application, simply open it in Android Studio and run the default build configuration. If this fails, make sure Android studio and all necessary plugins (gradle, etc) are up to date.

## Using the Application as a Co-developer

In order to run this application as is, a Google account is needed, as ChirpMeIn relies on a serverless backend implementation using Google Firebase.
The application will not work correctly as is unless you are directly added as a developer, as Google firebase services will refuse any requests
from the application unless you are a verified user. If you are interested in joining the development of ChirpMeIn, you can send me your 
SHA-256 debug key from Android Studio by running 

```
gradle signingreport
```

from within Android studio, and sending me the SHA-256 value, and I can add you as a developer.
  
## To build and develop independently of myself and my partner
  
If you would like to develop independently, then you will need to set up a new Android project in the Google Firebase console with the package name chirp.me.in
  
This setup process is explained neatly [here](https://firebase.google.com/docs/android/setup#:~:text=Open%20the%20Firebase%20Assistant%3A%20Tools,your%20Android%20project%20with%20Firebase.), though you can skip any steps after 3.1. (The gradle files are already configured to work with all the Firebase services needed by this project). Once the correct google-services.json file is in your repository, the build should run and the app should allow you to authenticate with your own Google credentials. From there, you will need to setup the Web application side of the Firebase console, the instructions for which can be found [here](https://firebase.google.com/docs/web/setup). Note that since the project is already set up, you can skip step 1 and instead just directly add a new web app from the ChirpMeIn project you created in the Firebase console. The rest of the instructions should follow as described.

# File Descriptions
   
The project is structured in the usual format for Android projects, with the primary source code in [ChirpMeIn\app\src\main\java\chirp\me\in](https://github.com/One2Remember/ChirpMeIn/tree/master/app/src/main/java/chirp/me/in). For full documentation, you can simply clone the project to a local repository and open javadoc/index.html in your web browser. A short summary of the documentation can be found below as well
   
## [activites/LoginActivity.java](https://github.com/One2Remember/ChirpMeIn/blob/master/app/src/main/java/chirp/me/in/activities/LoginActivity.java)
  
This file is the only Activity used in the web application. It allows the user to sign in or out with Google credentials, and invokes helper classes to begin the ChirpMeIn authentication protocol in the background.
  
## [utils/FFT.java](https://github.com/One2Remember/ChirpMeIn/blob/master/app/src/main/java/chirp/me/in/utils/FFT.java)
  
Performs discrete Fast Fourier Transform on data. Based on the algorithms originally published by E. Oran Brigham "The Fast Fourier Transform" 1973, in ALGOL60 and FORTRAN
  
## [utils/FirebaseHelper.java](https://github.com/One2Remember/ChirpMeIn/blob/master/app/src/main/java/chirp/me/in/utils/FirebaseHelper.java)
  
Helper class for all communications with Firebase. This class performs authentication procedures, as well as the ChirpMeIn communication protocol which relies primarily on manipulation of Firebase flags.
  
## [utils/LinearRegression.java](https://github.com/One2Remember/ChirpMeIn/blob/master/app/src/main/java/chirp/me/in/utils/LinearRegression.java)
  
Helper class for performing linear regression. Used in feature extraction on processed audio wave form in the frequency domain.
  
## [utils/RecordingHelper.java](https://github.com/One2Remember/ChirpMeIn/blob/master/app/src/main/java/chirp/me/in/utils/RecordingHelper.java)
  
Helper class for starting and stopping recording.
  
## [utils/SoundProcessor.java](https://github.com/One2Remember/ChirpMeIn/blob/master/app/src/main/java/chirp/me/in/utils/SoundProcessor.java)
  
Helper class for all sound processing. This is the class that invokes FFT on the recorded audio, and performs all signal processing, including creation of spectrograms. The primary method is getLinearRegression(final long latencyMS), which returns a LinearRegression object based on a parametrized latency (in milliseconds). The constructor for this class takes in the file path for the wav file which will be processed.

## [utils/WavMaker.java](https://github.com/One2Remember/ChirpMeIn/blob/master/app/src/main/java/chirp/me/in/utils/WavMaker.java)
  
Helper class used for converting raw audio data to .wav format and saving to local file.
  
### Other Notable Files

ChirpMeIn/src/main/res/values contains resource files that are used throughout the application. [integers.xml](https://github.com/One2Remember/ChirpMeIn/blob/master/app/src/main/res/values/integers.xml), in particular, contains the codes for each flag state within Google Firebase, used for tracking the protocol state at any given point in time. Additionally, [bools.xml](https://github.com/One2Remember/ChirpMeIn/blob/master/app/src/main/res/values/bools.xml) contains the DEBUG value, which can be set to true or false to run the application in debug mode (which prints debug data while running and also saves and uploads the recording.wav and spectrogram.png file for each authentication instance).
