
## Interface Navigator

Interface Navigator is an Android App that uses TensorFlow Mobile and transfer learning to recognize head movements and classify them in 5 known classes. 

This great demo of Tensorflow.js: https://storage.googleapis.com/tfjs-examples/webcam-transfer-learning/dist/index.html was an inspiration for me to create a similar camera gesture recognition software, but on a mobile platform.
It uses a custom model retrained from https://tfhub.dev/google/imagenet/mobilenet_v2_140_224/classification/1 (**--tfhub_module** in **retrain.py**), so there is no need to take pictures and perform online training step.

The app recognizes 5 movements:
- up
- down
- left
- right
- center (to confirm a choice)

and adjusts the token position accordingly.

I'm still working on its accuracy, so far up/down/center movements are recognized with best precision, left/right are sometimes mixed up.

### Build

For app testing and APK generation run: ./gradlew assembleDebug, this will generate APK in interface-navigation/app/build/outputs/apk/debug 