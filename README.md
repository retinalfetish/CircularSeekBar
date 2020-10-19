# CircularSeekBar
A styleable circular [SeekBar](https://developer.android.com/reference/android/widget/SeekBar) widget. The user can initiate changes to the progress level by moving a draggable thumb or touching the drawn arc.
 
## Screenshots
<img src="/art/screenshot-default.png" alt="Screenshot" height=600> <img src="/art/screenshot-arc.png" alt="Screenshot" height=600>
 
## Usage
The source code can be copied from the single class file and attrs.xml in to your project or included by adding [jitpack.io](https://jitpack.io/#com.unary/circularseekbar) to the root build.gradle and `implementation 'com.unary:circularseekbar:1.1.0'` as a module dependency.
```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```
This widget has a number of options that can be configured in both the XML and code. An example app is provided in the project repository to illustrate its use and the `OnProgressChangeListener` interface.
```
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <com.unary.circularseekbar.CircularSeekBar
        android:id="@+id/seek_bar"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center" />

</FrameLayout>
```
Drift, gravity, and snap scroll modes:

<img src="/art/screenshot-drift.gif" alt="Screenshot" height=400> <img src="/art/screenshot-gravity.gif" alt="Screenshot" height=400> <img src="/art/screenshot-snap.gif" alt="Screenshot" height=400>

The listener interface:
```
seekBar.setOnProgressChangeListener(this);

@Override
public boolean onProgressChanging(CircularSeekBar seekBar, int progress) { ... }

@Override
public void onProgressChanged(CircularSeekBar seekBar, int progress, boolean finished) { ... }
```

### XML attributes
The following optional attributes can be used to change the look and feel of the view:
```
app:max="integer"                   // Default value of 100
app:min="integer"                   // Should not be less than 0
app:progress="integer"              // Default of 0 and must be within the min/max range
app:progressColor="reference|color" // Reference to a color selector or simple color
app:scrollMode="drift|gravity|snap" // Default mode is "drift"
app:startAngle="float"              // Starting angle, relative to 90 degrees clockwise
app:strokeWidth="dimension"         // Thickness of the arc. Default is "14dp"
app:sweepAngle="float"              // Arc sweep angle, clockwise from the starting angle
app:sweepColor="color"              // Color used to draw the arc
app:thumbDrawable="reference"       // Reference to a drawable
app:thumbRadius="dimension"         // Radius of the drawable. Default is "12dp"
app:touchInside="boolean"           // Respond to touch inside the ellipse

android:enabled="boolean"           // Changes the view state and progress color
```
