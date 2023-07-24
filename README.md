# MobiSpectral Application on Android
This application is written in Kotlin for Android phones. It works in two modes: offline and online. The offline mode processes pre-captured images, while the online mode captures and processes the images in real time. The online mode requires a phone that allows accessing the NIR camera. In both offline and online modes, the application offers two functions:

- Simple Analysis: Distinguishes organic and non-organic fruits from RGB and NIR images.  The app asks the user to select a region of the image to be used in the analysis: by tapping anywhere in the image, a bounding box will appear. The app will then process the data in the bounding box and will display Organic or Non-Organic.

- Detailed Spectral Analysis: Allows the user to conduct an in-depth analysis of the hyperspectral bands reconstructed from the input RGB+NIR images. When a user taps on an area in the image, the app will compute and display the spectral signature for the pixels in that area.

## Requirements
- Offline mode: Any Android smartphone should work.
- Online mode: Android phones that allow access to the  NIR camera, such as Google Pixel 4 XL and OnePlus 8 Pro*.

\* For OnePlus 8 Pro, the camera with NIR information was removed using software due to [privacy concerns](https://www.theverge.com/2020/5/15/21259723/oneplus-8-pro-x-ray-vision-infrared-filter-see-through-plastic). It can be accessed in OxygenOS 11 using [Factory Mode](https://www.xda-developers.com/oneplus-8-pro-color-filter-camera-still-accessible-adb-command/ "How to access Color Filter Camera on OnePlus 8 Pro using adb") or by rooting the phone in OxygenOS 12.

## Install the App
The APK for the application can be downloaded from here: [MobiSpectral APK](https://drive.google.com/file/d/18bQZj7JiFfU4paXye6SbOPJlbtXJHpEB/view?usp=sharing "MobiSpectral Android Application"). There are Information buttons ⓘ on each page (Fragment), which tells the user what to do.

Steps for installing the application on your phone are as follows:

- Press the APK file to install the application.
- If the application could not be downloaded, disable Play Protect (a service from Google that prevents users from installing applications that are not from Play Store) on your phone for the time being. To do that, follow the steps below:
	- Open `Google Play Store`.
	- Click your Profile Picture (top right).
	- Open `Play Protect` option and press the Settings Cog (top right).
	- Disable the option `Scan apps with Play Protect`
- After disabling "Play Protect" press APK file and, a pop-up will appear, asking you to allow downloading apps from other sources.
- When the application is installed, the app will ask for permission to access the camera and storage on your phone.
	- Camera Permission: required to capture images using the cameras.
	- Storage Permission: required to save and load images from the disk.
- The application will be installed, and you will reach the Main page of the application.

You can also [build the application from source code](#build-the-application-from-the-source-code).

## Test the Application in Offline Mode
Download one or more of the following Mobile Image datasets to test the application:

- [Apples (90.2 MB)](https://drive.google.com/file/d/1gV-Y31WzwILBqPNgJcsLAqMJTp7ua0JU/view?usp=drive_link "Apples Test Dataset")
- [Kiwis (39.1 MB)](https://drive.google.com/file/d/1h2k2gD4A4KZEmmcGJFEAhxpVNv0QSHgF/view?usp=drive_link "Kiwis Test Dataset")
<!-- - [Blueberries (68.6 MB)](https://drive.google.com/file/d/1g_yICRC79qbsJH2hfTdv5RhSIgsOwkOc/view?usp=drive_link "Blueberries Test Dataset")
- [Tomatoes (46.6 MB)](https://drive.google.com/file/d/14XfBuJtO4k_CIRyumhy-Wk77tDJ_BopV/view?usp=drive_link "Tomatoes Test Dataset")
- [Strawberries (60.1 MB)](https://drive.google.com/file/d/1-nJkoCrELbjaYDh7FrhMUqnB9xe8r1kj/view?usp=drive_link "Strawberries Test Dataset") -->

If you have all of the datasets already downloaded (for the reconstruction and classification phase) these `mobile_data` directories are also present in them but if you wish to download just the `mobile_data` you can do so from these links. The datasets are in pairs of RGB and NIR images. Each dataset has the following directory structure, where `[fruit]` is apples, kiwis, ...:
```
dataset_[fruit]
│
└── mobile_data
	│
	└── nonorganic
	│	[num]_NIR.jpg
	|	[num]_RGB.jpg
	|	...
	|
	└── organic
	│	[num]_NIR.jpg
	|	[num]_RGB.jpg
	|	...

```

Steps to run the application in the offline mode:
1. Unzip the dataset that you downloaded before
2. Run the application
3. Check the offline mode to be used
	- `mobile_data` is the folder where you can select images, either from the organic or nonorganic sub-folders
4. Select two corresponding images (RGB and NIR) from the pop-up by tapping
5. (Optional) Tap to choose the region that will be used in the analysis (bounding box)
6. Reconstruct the hypercube
7. The application shows the predicted classification label for the fruit

The application screenshots below are captured using a smartphone without an NIR camera:

| | | |
:-------------------------:|:-------------------------:|:-------------------------:
| <img src="images/MainPage(NoNIR).jpg" alt="MainPage(NoNIR)" width="200" /> | <img src="images/MainPageOffline.jpg" alt="MainPage" width="200" /> | <img src="images/CameraOffline.jpg" alt="Camera" width="200" /> |
| Main Page if No NIR Camera is found | Selecting Offline Mode | Gallery Opening Intent |
| <img src="images/ImageLoader.jpg" alt="ImageLoader" width="200" /> | <img src="images/ImageViewer.jpg" alt="ImageViewer" width="200" /> | <img src="images/Classification.jpg" alt="Classification" width="200" /> |
| Selecting Images | Image Viewer | Classification Result |

## Test the Application in Online Mode
This mode requires a phone that allows accessing the NIR camera. Most phones with NIR cameras have them on the front because their primary use has so far been face identification. To assist the user in capturing fruit images using front-facing cameras, we added a countdown timer (3 sec) that makes the app issues a beeping sound after it captures the images. The Online mode also makes sure that the scene is well lit before the user can capture any picture.

Steps to run the application in the online mode:
1. Run the application (ensure the offline mode is checked off)
2. Press the capture button, and turn the phone towards the fruit. It will beep after capturing the images
4. (Optional) Tap to choose the region that will be used in the analysis (bounding box)
6. Reconstruct the hypercube
7. The application shows the predicted classification label for the fruit

Here are the screenshots from the android application (captured using Google Pixel 4XL):

| | | |
:-------------------------:|:-------------------------:|:-------------------------:
| <img src="images/MainPageOnline.jpg" alt="MainPage" width="200" /> | <img src="images/CameraOnline.png" alt="Camera" width="200" /> | <img src="images/Classification.jpg" alt="Classification" width="200" /> |
| Main Page | Camera Fragment | Classification Results |

## Simple and Detailed Analysis
The difference between Simple and Detailed Analysis, as mentioned in [Section 1](#mobispectral-application-on-android), is that Detailed analysis allows (the user can also tap to get a smaller working area) the user to reconstruct the whole Hypercube. Reconstructing whole hypercube takes takes a lot more time. In the Detailed Analysis, you can tap on parts of the reconstructed hypercube bands, to get the signatures of that pixels and their predicted organic/nonorganic class. The images shown in the image sets above are for simple analysis where as the images below show their differences to detailed analysis:

| | | |
:-------------------------:|:-------------------------:|:-------------------------:
| <img src="images/ImageViewer(Detailed).jpg" alt="ImageViewer Detailed" width="200" /> | <img src="images/Reconstruction(Detailed).jpg" alt="Reconstructed" width="200" /> | <img src="images/SignatureAnalysis(Detailed).jpg" alt="Classification" width="200" /> |
| Image Viewer in Detailed Analysis | Reconstructed Hypercube | Signature Analysis and Class Prediction |

<!-- ## Pipeline
1. Image Capturing: RGB followed by NIR.
3. Image Alignment: Aligning the two images captured.
4. Deep White Balancing: Android ported models from [[Deep White Balance](https://github.com/mahmoudnafifi/Deep_White_Balance), [Models](https://github.com/mahmoudnafifi/Deep_White_Balance/tree/master/PyTorch/models)].
5. Patch Selection: Selecting the part of image we want to use.
6. Hyperspectral Reconstruction: RGB+NIR -> Hypercube.
7. Classification: based on 1-D signatures selection. -->

## Build the application from the source code
Download Android Studio on your workstation (see Installation Instructions on [Android Developer website](https://developer.android.com/studio)). After Installing Android Studio, clone the repository onto your workstation. Gradle is a tool that comes pre-installed with Android Studio and is responsible for Dependency management. In the repository, there are also gradle files that tell gradle which dependencies to install on your workstation. The major dependencies which we tested and deployed are as follows:

- Android Studio Flamingo | 2022.2.1 Patch 2 (tested on previous versions as well)
- Kotlin 1.8.20 (Also tested on 1.7.21)
- Gradle: 8.0.2 (Also tested on 7.5.0, 7.4.0, 4.2.2)
- JDK Version: 18.0.2 [Amazon Corretto Version] (Also tested on 17, 16)
- SDK versions 33 (target), 23 (minimum)
- AndroidX
- Dependencies
	- PyTorch: 1.8.0 (versions above it contain some bugs and uses Lite Interpreter which did not convert models to PyTorch mobile version)
	- OpenCV: 3.4.3

When Android Studio is set up on your workstation, connect your Android Smartphone. Now enable USB debugging on your phone (first enable Developer options, and then go in there to enable USB debugging). Your device name and model should now appear in the box next to `Run 'app'` button [See Image below]. If it does not appear, allow `File Transfer` on your smartphone and tap on the USB connection/preferences notification. This [official guide](https://developer.android.com/studio/run/device "Guide to connect your phone to your PC") by Android Developers can be followed for this.

When the project is loaded onto your Android Studio, build the project using the `Build` button from the top bar and press `Make Project` [`CTRL + F9`]. To build the project, first run gradle to install all packages required by the project, and then build the project. After building the project, run the project by pressing the `Run 'app'` button as can be seen in the following picture [`SHIFT + F10`]. Simply pressing the Run app button does all previous steps in one go (Installing dependencies, Building Project, Running app on your phone).

![Run Project](images/RunApp.png)

One common issue that can occur while building projects on a recently installed Android Studio copy is the mismatch of JDK source/target version. You can match this dependency by pressing the "File" button and opening up Settings [`CTRL + ALT + S`]. Then on the left pane, expand `Build, Execution, Deployment` option, and click on `Gradle` under `Build Tools`. Now on this page, press Gradle JDK and set it to 18.0.2 [Amazon Corretto Version]; if its not installed, install it using the button below [See image below]. The structure of Android Studio may change, and these instructions are true for Android Studio Flamingo | 2022.2.1 Patch 2 version.

![Gradle JDK Version](images/JDKVersion.png)

## References
- Picture capturing code using Camera API 2 and was forked from [Android/Camera2Basic](https://github.com/android/camera-samples/tree/main/Camera2Basic) and built upon from there.
- Models from [Deep White Balancing](https://github.com/mahmoudnafifi/Deep_White_Balance) were ported to PyTorch Android.