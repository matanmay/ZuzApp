# ZuzApp – Movement Experiment Logger

ZuzApp is a native Android application designed for scientific experiments that require tracking device movement.  
The app captures accelerometer data, calculates movement magnitude, filters out noise, and logs the results to a CSV file.

---

## Features

- **Real-time Monitoring**  
  Displays live acceleration delta values on screen.

- **Noise Threshold Filtering**  
  Automatically sets movement to `0.00` if the change is below a threshold (`0.5f`) to filter out minor vibrations.

- **Data Logging**  
  Records experiment data to CSV files in the device's internal storage.

- **Session Management**  
  Allows manual entry of Experimenter Code and Session ID (or auto-generates a UUID).

- **Battery Efficiency**  
  Automatically unregisters sensors when the session is stopped or paused (if not recording).

---

## Technical Stack

- **Language:** Java  
- **Minimum SDK:** Android 7.0 (API 24)  
- **Target SDK:** Android 14 (API 34)  
- **Architecture:** MVVM-lite (Activity + Helper Classes)  
- **Build System:** Gradle  

---

## Installation

1. Clone or download this repository.
2. Open **Android Studio**.
3. Select **File > Open** and navigate to the project root directory.
4. Wait for the Gradle sync to complete.
5. Connect an Android device or start an Emulator.
6. Click **Run**.

---

## How to Use

1. **Enter Experimenter Code**  
   Type the ID of the person conducting the experiment.

2. **Enter Session ID (Optional)**  
   Type a specific session ID.  
   If left blank, a random UUID will be generated automatically.

3. **Start Session**  
   Click the **Start Session** button.  
   - The button will turn red  
   - The screen will stay on  

4. **Perform Experiment**  
   Move the device as required.

5. **Stop Session**  
   Click **Stop Session** to save the data.

---

## Accessing Data Logs

The application saves data to **app-specific internal storage** to ensure privacy and avoid external storage permission issues.

### Steps to retrieve CSV files:

1. Connect your Android device to your PC or Mac.
2. Open **Android Studio**.
3. Go to **View > Tool Windows > Device File Explorer**.
4. Navigate to:
5. Right-click the desired `.csv` file (e.g. `Experiment_20231027_103000.csv`).
6. Select **Save As…** to download it.

---

## Data Format

The output CSV contains the following columns:

| Column            | Description                                           |
|-------------------|-------------------------------------------------------|
| SessionID         | Unique identifier for the specific run                |
| ExperimenterCode  | ID entered by the user                                |
| Timestamp         | Absolute wall-clock time (`HH:mm:ss.SSS`)             |
| ElapsedTimeMs     | Milliseconds elapsed since **Start Session**          |
| Magnitude         | Calculated movement delta (|acceleration − gravity|) |

---

## Logic Details

The movement is calculated using the linear magnitude of the accelerometer vector:

```java
double magnitude = Math.sqrt(x*x + y*y + z*z);
float delta = (float) Math.abs(magnitude - 9.81);

// Threshold Filter
if (delta < 0.5f) {
 delta = 0.0f;
}
