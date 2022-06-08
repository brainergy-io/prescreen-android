[![](https://jitpack.io/v/brainergy-io/prescreen-android.svg)](https://jitpack.io/#brainergy-io/prescreen-android)

# PreScreen OCR Library

## Prerequisite

- Android SDK Version 21 or Higher
- Google Play Services
- `Internet` and `Camera` Permissions in Manfiest

## Setup Guide

- Add this to your `build.gradle` at the root of the project

  ```
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
  ```
- Add your dependency in your app or feature module as below
```
	dependencies {
		implementation 'com.github.brainergy-io:prescreen-android:<version>'
	}
```

- Also make sure to add this script to your `build.gradle` in app module to make sure the SDK run properly
```
    android {
        ...

        aaptOptions {
            noCompress "model"
        }

    }
```

- Sync your gradle project and PreScreen is now available for your application

## HOW-TO

1. Initialize SDK by calling `init` function with your API Key

    ```kotlin
    PreScreen.init(this, "API_KEY")
    ```

1. Create the `CardImage` object which will be used as an input for OCR

    ```kotlin
    val card = CardImage(this.image!!, imageInfo.rotationDegrees)
    ```

1. Call the `scanIDCard` method from `PreScreen` object class which will extract card information from the image. There are 3 required parameters.
    - `card`: Input card image
    - `resultListener`: The listener to receive recognition result. This will provide the information of cards that can be detected in `IDCardResult` object

      ```kotlin
      it.setAnalyzer(cameraExecutor) { imageProxy ->
              imageProxy.run {
                val card = CardImage(this.image!!, imageInfo.rotationDegrees)
                PreScreen.scanIDCard(card) {result ->
                  this@MainActivity.displayResult(result)
                  imageProxy.close()
                }
              }
            }
       ```

1. Use the resulting `IDCardResult` object will consist of the following fields:
    - `error`: If the recognition is successful, the `error` will be null. In case of unsuccessful scan, the `error.errorMessage` will contain the problem of the recognition.
    - `isFrontSide`: A boolean flag indicates whether the scan found the front side (`true`) or back side (`false`) of the card ID.
    - `confidence`: A value between 0.0 to 1.0 (higher values mean more likely to be an ID card).
    - `isFrontCardFull`: A boolean flag indicates whether the scan detect a full front-sde card.
    - `texts`: A list of OCR results. An OCR result consists of `type` and `text`.
        - `type`: Type of information. Right now, PreScreen support 3 types: `ID`, `SERIAL_NUMBER`, and `LASER_CODE`
        - `text`: OCR text based on the `type`.
    - `fullImage`: A bitmap image of the full frame used during scanning.
    - `croppedImage`: A bitmap image of the card. This is available if `isFrontSide` is `true`.
    - `classificationResult`: A result from ML image labeling, available if `isFrontCardFull` is `true`.
	  - `confidence`: A confidence value from 0 to 1. Higher values mean the images are more likely to be good quality. The threshold of `0.6` to `0.9` is recommended. 
	  - `error`: An object for error messages.

    ```kotlin
    private fun displayResult(result: IDCardResult) {
          result.error?.run {
              resultText.text = errorMessage
          }
          boundingBoxOverlay.post{boundingBoxOverlay.clearBounds()}
          val bboxes: MutableList<Rect> = mutableListOf()
          var mlBBox: Rect? = null
          result.run {
              // fullImage is always available.
              val capturedImage = fullImage
  
              confidenceText.text = "%.3f ".format(confidence)
              if (this.detectResult != null) {
                  confidenceText.text = "%.3f (%.3f) (%.3f)".format(
                      confidence,
                      this.detectResult!!.mlConfidence,
                      this.detectResult!!.boxConfidence)
                  if (this.detectResult!!.cardBoundingBox != null) {
                      mlBBox = this.detectResult!!.cardBoundingBox!!.transform()
                  }
              }
              if (isFrontSide != null && isFrontSide as Boolean) {
                  // cropped image is only available for front side scan result.
                  val cardImage = croppedImage
                  confidenceText.text = "%s, Full: %s".format(confidenceText.text, isFrontCardFull)
  
                  if (classificationResult != null && classificationResult!!.error == null) {
                      confidenceText.text = "%s (%.3f)".format(confidenceText.text, classificationResult!!.confidence)
                  }
              }
  
              if (texts != null) {
                  resultText.text = "TEXTS -> ${texts!!.joinToString("\n")}, isFrontside -> $isFrontSide"
              } else {
                  resultText.text = "TEXTS -> NULL, isFrontside -> $isFrontSide"
              }
              if (idBBoxes != null) {
                  bboxes.addAll(idBBoxes!!)
              }
              if (cardBox != null) {
                  bboxes.add(cardBox!!)
              }
              boundingBoxOverlay.post{boundingBoxOverlay.drawBounds(
                 bboxes.map{it.transform()}, mlBBox)}
          }
      }
      ```

### Example Code
Please check on the demo app for further reference.
       


