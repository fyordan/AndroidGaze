# AndroidGaze
Project for Smartphone Vision. Android app that tracks your Iris and predicts whether you are looking up/down, and left/right.

### Introduction

Gaze tracking refers to being able to predict where a user is looking at by extracting features from a image.
It has applications in road safety, by predicting whether a driver is alert and focused. In accessibility where a user might be able to control a phone by looking at specific areas,
and also in marketing and ux/design where it is important to track which elements in a page might draw the user's attention.
It can be computationally expensive, since some of the methods require either processing the image through a CNN,
as demonstrated by [I-Tracker](http://gazecapture.csail.mit.edu/cvpr2016_gazecapture.pdf).

This project however only uses Android SDK and Google GMS libraries, making the Android app very lightweight.
The code is inspired from the IEEE paper by [Anjith George and Aurobinda Routray](https://arxiv.org/pdf/1605.05272.pdf) for low resolution gaze tracking
and the Institute for Neuro-and Bioinformatics paper by [Fabian Timm and Erhardt Barth](http://www.inb.uni-luebeck.de/publikationen/pdfs/TiBa11b.pdf) for Eye Centre localisation by means of gradients.

##### Disclaimer

All this was tested only with a Galaxy Note S3 using the front facing camera, and a person with brown eyes. Code might not be generalized enough to work well with other (for better or worse) camera resolutions, slower processors, or brighter eyes.

Additionally, some assumptions as to how far away the phone would be were made (Roughly 1.3 feet away from head) as most of my testing was done by me in selfie mode.
The head pose of the person is also assumed to be facing the camera and fairly consistent.

### Methods

There are roughly three steps in Gaze Prediction:

1. Find General Eye Position
  * Can be done fast using HAAR Cascade Classifiers 
  * Can take advantage of com.google.gms.vision.face.FaceDetector
2. Find Center of Iris
  * Uses Gradients to find center of dark circle
3. Given location of Iris, predict gaze position

This code focuses mostly on aspect 2. It takes advantage of the gms libraries to do 1, and instead of predicting exact gaze position, distinguishes only between up/down, and left/right.

#### Find General Eye Position.
As stated, this can be done fairly simply using the gms library. The FaceDetector API can provide multiple Face objects and a callback can be written to do extra image processing on each Face object.
It also provides the information of where landmarks are located, such as the Right and Left Eye. 

In this code we throw away all information except the location of the Right Eye (and only use that eye in order to not waste time processing both eyes).
Then we grab a rectangular region centered at the eye location with a height of eyeRegionHeight=60 and a width of eyeRegionWidth=80 pixels.
This eyeImage is stored unto a Bitmap and grayscaled.

The resulting image is fairly small, so it must be upsampled to be displayed for debugging purposes.

#### Find Center of Iris

##### Gradient Field
First, we need to create a gradient for the bitmap image. This is done by iterating through every pixel and taking the difference between said pixel and the previos pixel in x and y coordinates.
To ignore edge conditions, we start at the second row and second column, and end in the second to last row and column. This gives the partial derivative of brightness in both x and y coordinates.
From here we calculate the magnitude and divide the two components in order to turn the gradient into a unit vector. 
The gradients are stored in a double array[][] where the first index correspond to the pixel number and the second to the x or y coordinate. This was faster than using a tuple data type.


##### Center of Iris
The main idea of this algorithm is the assumption that the iris can be reduced to a circle with very strong gradients at its circunference.
Additionally, given any point C inside the eye, the dot product between the unit vector from C to the gradient at an edge of the iris, and the gradient would be maximum if they were both pointing in the same direction.
This is only true for all gradients in the iris if the point C is at the center of the circle.

Therefore the point is to find the coordinate C such that it maximizes the equation

##### Optimizations
