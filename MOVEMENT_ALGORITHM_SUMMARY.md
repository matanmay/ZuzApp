# ZuzApp Movement Algorithm Summary

## Overview
ZuzApp is an Android application designed to track and log movement data from a device (typically a phone placed on a chair). It combines gyroscope and rotation vector sensor data to capture both angular velocity (rate of rotation) and absolute orientation angles.

## Sensors Used

### 1. Gyroscope (TYPE_GYROSCOPE)
- **Purpose**: Measures angular velocity (rate of rotation)
- **Units**: Radians/second (converted to degrees/second)
- **Axes**: X, Y, Z (currently using Z-axis for rotation)
- **Data Type**: Velocity (how fast the device is rotating)

### 2. Rotation Vector (TYPE_ROTATION_VECTOR)
- **Purpose**: Provides absolute orientation relative to Earth's coordinate system
- **Output**: Pitch, Roll, Yaw angles
- **Units**: Degrees
- **Data Type**: Position (absolute angle at any moment)

## Calibration Process

### Auto-Calibration on Startup
The app automatically calibrates when it starts. Users can also manually recalibrate using the calibration button.

### What Gets Calibrated

#### 1. Gyroscope Baseline Noise
- **Sample Count**: 50 samples
- **Method**: Averages the absolute value of gyroscope readings while device is stationary
- **Purpose**: Removes sensor drift and noise from gyroscope measurements
- **Formula**: `baselineNoise = sum(|rawDelta|) / 50`

#### 2. Yaw Baseline
- **Sample Count**: 50 samples
- **Method**: Averages the absolute value of yaw readings during calibration
- **Purpose**: Establishes a "reference point" for relative yaw measurements
- **Formula**: `baselineYaw = sum(|yaw|) / 50`

### Calibration Requirements
- Device must be kept completely still
- Both gyroscope and rotation vector sensors must be available
- Takes 50 samples to complete

## Data Processing

### Gyroscope Data Processing

1. **Read Raw Value**: Z-axis gyroscope reading (radians/sec)
2. **Convert to Degrees**: `rawDelta = toDegrees(z_axis_value)`
3. **Subtract Baseline**: `magnitude = max(0, |rawDelta| - baselineNoise)`
4. **Restore Sign**: `delta = copySign(magnitude, rawDelta)`
5. **Apply Threshold**: If `|delta| < 0.5 deg/s`, set `delta = 0`

**Result**: 
- `delta`: Calibrated angular velocity with direction (positive/negative)
- `rawDelta`: Original uncalibrated reading

### Rotation Vector Data Processing

1. **Get Rotation Matrix**: Convert rotation vector to 3x3 rotation matrix
2. **Extract Orientation**: Calculate pitch, roll, yaw from rotation matrix
3. **Convert to Degrees**: All angles converted from radians to degrees
4. **Calibrate Yaw**: `calibratedYaw = currentYaw - baselineYaw`

**Result**:
- `pitch`: Forward/backward tilt (uncalibrated)
- `roll`: Left/right tilt (uncalibrated)
- `calibratedYaw`: Relative rotation from starting position
- `rawYaw`: Absolute yaw angle

## Logged Data

### CSV Format
Each movement sample is logged with the following columns:

| Column | Type | Description |
|--------|------|-------------|
| SessionID | String | Unique session identifier |
| ExperimenterCode | String | Subject/experimenter identifier |
| Timestamp | Time | HH:mm:ss.SSS format |
| ElapsedTimeMs | Long | Milliseconds since session start |
| Magnitude | Float | Calibrated gyroscope magnitude (signed) |
| RawDelta | Float | Raw gyroscope reading (deg/s) |
| Pitch | Float | Forward/backward tilt (degrees) |
| Roll | Float | Left/right tilt (degrees) |
| CalibratedYaw | Float | Rotation from calibrated position (degrees) |
| RawYaw | Float | Absolute yaw angle (degrees) |

### Data Storage
- **Local**: CSV files in app's internal storage
- **Cloud**: Batch upload to Supabase (20 records per batch)
- **Filename Format**: `SubjectName__SessionID__yyyyMMdd_HHmmss.csv`

## Movement Detection Logic

### Noise Filtering
- **Gyroscope Threshold**: 0.5 deg/s (after baseline subtraction)
- **Purpose**: Ignore micro-movements and sensor noise
- **Implementation**: Values below threshold are set to 0

### Continuous Logging
- Logs are written at sensor update rate (~60-100 Hz with SENSOR_DELAY_GAME)
- All values are logged, including zeros (to maintain complete timeline)
- Preserves direction information (positive/negative rotation)

## Use Case: Chair Movement Tracking

### Setup
- Phone is placed flat on the chair seat
- Screen facing up or down

### What Each Sensor Captures

#### Z-Axis Gyroscope:
- **Measures**: Spinning/swiveling motion
- **Use**: Detects rotation speed and direction
- **Example**: Chair rotating clockwise vs counterclockwise

#### Rotation Vector (Yaw):
- **Measures**: Total rotation angle from starting position
- **Use**: Tracks cumulative rotation
- **Example**: Chair has rotated 45° from calibrated position

#### Rotation Vector (Pitch/Roll):
- **Measures**: Tilt angles
- **Use**: Could detect chair reclining or tilting
- **Note**: Currently logged but may be less relevant for spinning chair

## Algorithm Characteristics

### Strengths
1. **Dual Sensor Approach**: Combines velocity (gyroscope) and position (rotation vector) data
2. **Direction Preservation**: Maintains sign of rotation (clockwise vs counterclockwise)
3. **Noise Reduction**: Calibration removes sensor drift and baseline noise
4. **Complete Data**: Logs both raw and processed values for analysis flexibility
5. **High Frequency**: Fast sampling rate captures rapid movements

### Limitations
1. **Gyroscope Drift**: Long sessions may accumulate integration error (mitigated by calibration)
2. **Magnetic Interference**: Yaw angle may be affected by nearby magnetic fields
3. **Single Axis Focus**: Currently primarily uses Z-axis rotation
4. **No Integration**: Gyroscope data not integrated to calculate angle (relies on rotation vector for absolute angle)

## Session Workflow

1. **App Launch**: Auto-calibration starts (50 samples, ~1 second)
2. **Calibration Complete**: Baseline noise and yaw stored
3. **Start Session**: User enters code, optional session ID
4. **Recording**: Both sensors active, continuous logging at ~60-100 Hz
5. **Stop Session**: 
   - Final batch uploaded to Supabase
   - CSV file closed
   - File path displayed to user

## Technical Details

### Sensor Delay Mode
- **Setting**: `SENSOR_DELAY_GAME`
- **Expected Rate**: ~60-100 Hz
- **Trade-off**: Balance between data resolution and battery/performance

### Thread Safety
- All sensor callbacks run on main thread
- File writes are synchronous (flushed immediately)
- Supabase uploads are asynchronous

### Error Handling
- Checks for sensor availability before starting
- Graceful fallback if sensors unavailable
- Exception catching for file I/O and network operations

## Future Enhancement Possibilities

1. **Multi-Axis Analysis**: Incorporate X and Y gyroscope axes for more complex movements
2. **Gyroscope Integration**: Calculate angle by integrating gyroscope data (compare with rotation vector)
3. **Movement Classification**: Detect specific movement patterns (spinning, rocking, etc.)
4. **Real-time Visualization**: Display movement graphs during recording
5. **Adaptive Thresholding**: Adjust noise threshold based on environment
6. **Sensor Fusion**: Combine gyroscope and rotation vector for improved accuracy