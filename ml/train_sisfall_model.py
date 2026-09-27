import os
import glob
import json
import numpy as np
from scipy.interpolate import interp1d
import tensorflow as tf
from tensorflow.keras import layers, models
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score, precision_score, recall_score, f1_score

# --- Configuration & Paths ---
RAW_DATA_DIR = r"c:\Users\swathi\Downloads\rakshak-master\rakshak-master\ml\data\raw\SisFall_dataset"
OUTPUT_DIR = r"c:\Users\swathi\Downloads\rakshak-master\rakshak-master\ml\output"
TFLITE_OUTPUT_PATH = r"c:\Users\swathi\Downloads\rakshak-master\rakshak-master\app\src\main\assets\models\crash_classifier.tflite"

os.makedirs(OUTPUT_DIR, exist_ok=True)
os.makedirs(os.path.dirname(TFLITE_OUTPUT_PATH), exist_ok=True)

# --- Subject Split (by Subject ID, not windows) ---
# Total subjects = 38 (SA01-SA23: young, SE01-SE15: elderly)
# Train: 26 subjects (~70%), Val: 6 subjects (~15%), Test: 6 subjects (~15%)
TRAIN_SUBJECTS = {
    'SA01', 'SA02', 'SA03', 'SA04', 'SA05', 'SA06', 'SA07', 'SA08', 'SA09', 'SA10',
    'SA11', 'SA12', 'SA13', 'SA14', 'SA15', 'SA16',
    'SE01', 'SE02', 'SE03', 'SE04', 'SE05', 'SE06', 'SE07', 'SE08', 'SE09', 'SE10'
}
VAL_SUBJECTS = {'SA17', 'SA18', 'SA19', 'SE11', 'SE12', 'SE13'}
TEST_SUBJECTS = {'SA20', 'SA21', 'SA22', 'SA23', 'SE14', 'SE15'}

# --- Conversion Constants (Official SisFall Specs) ---
# ADXL345: 13-bit resolution, +/-16g range -> 1 LSB = 4 mg = 0.00390625 g
# g to m/s^2 conversion: 1 g = 9.80665 m/s^2
# Total factor: 0.00390625 * 9.80665 = 0.0383072265625 m/s^2 per LSB
ADXL_FACTOR = (16.0 / 4096.0) * 9.80665

# ITG3200: +/-2000 deg/s range, 14.375 LSB/(deg/s) -> 1 LSB = (1/14.375) deg/s
# deg/s to rad/s conversion: (pi / 180) = 0.017453292519943295
# Total factor: (1 / 14.375) * (pi / 180) = 0.001214142088343867 rad/s per LSB
ITG_FACTOR = (1.0 / 14.375) * (np.pi / 180.0)

RAW_WINDOW_LEN = 400  # 2 seconds at 200 Hz
TARGET_WINDOW_LEN = 100  # Resampled 50 Hz target
CHANNELS = 6  # accelX, accelY, accelZ, gyroX, gyroY, gyroZ

def parse_and_convert_file(file_path):
    """
    Parse raw text file from SisFall.
    Extract columns 0..5:
      Cols 0,1,2: ADXL345 Accel (LSB) -> convert to m/s^2
      Cols 3,4,5: ITG3200 Gyro (LSB)  -> convert to rad/s
    Returns np.ndarray of shape (N, 6) in float32.
    """
    raw_data = []
    with open(file_path, 'r') as f:
        for line in f:
            line = line.strip().rstrip(';').rstrip(',')
            if not line:
                continue
            parts = [p.strip() for p in line.split(',') if p.strip()]
            if len(parts) >= 6:
                try:
                    vals = [float(parts[i]) for i in range(6)]
                    raw_data.append(vals)
                except ValueError:
                    continue
    if not raw_data:
        return None
    
    arr = np.array(raw_data, dtype=np.float32)
    # Convert ADXL345 (cols 0,1,2)
    arr[:, 0:3] = arr[:, 0:3] * ADXL_FACTOR
    # Convert ITG3200 (cols 3,4,5)
    arr[:, 3:6] = arr[:, 3:6] * ITG_FACTOR
    return arr

def resample_window(window_400x6, target_len=100):
    """
    Linear interpolation of (400, 6) window to (100, 6).
    """
    src_len = window_400x6.shape[0]
    x_src = np.linspace(0, 1, src_len)
    x_target = np.linspace(0, 1, target_len)
    
    resampled = np.zeros((target_len, CHANNELS), dtype=np.float32)
    for c in range(CHANNELS):
        f = interp1d(x_src, window_400x6[:, c], kind='linear')
        resampled[:, c] = f(x_target)
    return resampled

def process_dataset():
    """
    Process SisFall files into windowed datasets divided by subjects.
    """
    txt_files = glob.glob(os.path.join(RAW_DATA_DIR, "**", "*.txt"), recursive=True)
    txt_files = [f for f in txt_files if not os.path.basename(f).startswith("Readme")]

    train_windows, train_labels = [], []
    val_windows, val_labels = [], []
    test_windows, test_labels = [], []

    print(f"Found {len(txt_files)} data files. Processing windows...")

    for fpath in txt_files:
        fname = os.path.basename(fpath)
        # Determine label: D01-D19 = 0 (ADL), F01-F15 = 1 (Fall)
        if fname.startswith('D'):
            label = 0
        elif fname.startswith('F'):
            label = 1
        else:
            continue

        # Extract subject ID (e.g., D01_SA01_R01.txt -> SA01)
        parent_dir = os.path.basename(os.path.dirname(fpath))
        subject_id = parent_dir  # SA01, SE01, etc.

        data = parse_and_convert_file(fpath)
        if data is None or len(data) < RAW_WINDOW_LEN:
            continue

        # Non-overlapping 2-second windows (400 samples), strictly within file boundary
        num_windows = len(data) // RAW_WINDOW_LEN
        for w_idx in range(num_windows):
            start = w_idx * RAW_WINDOW_LEN
            end = start + RAW_WINDOW_LEN
            w_400 = data[start:end]
            
            w_100 = resample_window(w_400, TARGET_WINDOW_LEN)

            if subject_id in TRAIN_SUBJECTS:
                train_windows.append(w_100)
                train_labels.append(label)
            elif subject_id in VAL_SUBJECTS:
                val_windows.append(w_100)
                val_labels.append(label)
            elif subject_id in TEST_SUBJECTS:
                test_windows.append(w_100)
                test_labels.append(label)

    X_train = np.array(train_windows, dtype=np.float32)
    y_train = np.array(train_labels, dtype=np.int32)
    X_val = np.array(val_windows, dtype=np.float32)
    y_val = np.array(val_labels, dtype=np.int32)
    X_test = np.array(test_windows, dtype=np.float32)
    y_test = np.array(test_labels, dtype=np.int32)

    return X_train, y_train, X_val, y_val, X_test, y_test

def compute_and_save_normalization(X_train):
    """
    Calculate per-channel mean and std using TRAINING data ONLY.
    Save to normalization_params.json.
    """
    # X_train shape: [N, 100, 6]
    # Calculate mean and std along axis (0, 1) -> shape (6,)
    means = np.mean(X_train, axis=(0, 1)).astype(float).tolist()
    stds = np.std(X_train, axis=(0, 1)).astype(float).tolist()

    # Prevent division by zero
    stds = [s if s > 1e-6 else 1.0 for s in stds]

    norm_params = {
        "channel_means": means,
        "channel_stds": stds,
        "channels": ["accelX", "accelY", "accelZ", "gyroX", "gyroY", "gyroZ"],
        "units": ["m/s^2", "m/s^2", "m/s^2", "rad/s", "rad/s", "rad/s"]
    }

    params_path = os.path.join(OUTPUT_DIR, "normalization_params.json")
    with open(params_path, "w") as f:
        json.dump(norm_params, f, indent=2)

    print(f"Saved normalization parameters to: {params_path}")
    return np.array(means, dtype=np.float32), np.array(stds, dtype=np.float32), norm_params

def apply_normalization(X, means, stds):
    return (X - means) / stds

def build_1d_cnn_model(input_shape=(100, 6)):
    """
    Small, lightweight 1D CNN for mobile TFLite deployment.
    Input shape: [100, 6]
    Output: binary probability (sigmoid) or 2 classes (softmax).
    We use Softmax with 2 output units [prob_normal, prob_crash].
    """
    model = models.Sequential([
        layers.Input(shape=input_shape),
        layers.Conv1D(filters=32, kernel_size=5, activation='relu', padding='same'),
        layers.BatchNormalization(),
        layers.MaxPooling1D(pool_size=2),  # -> 50
        
        layers.Conv1D(filters=64, kernel_size=3, activation='relu', padding='same'),
        layers.BatchNormalization(),
        layers.MaxPooling1D(pool_size=2),  # -> 25
        
        layers.Conv1D(filters=64, kernel_size=3, activation='relu', padding='same'),
        layers.GlobalAveragePooling1D(),
        
        layers.Dense(32, activation='relu'),
        layers.Dropout(0.3),
        layers.Dense(2, activation='softmax')  # [0=normal, 1=crash]
    ])

    model.compile(
        optimizer='adam',
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    return model

def export_tflite(model, output_path):
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    # Enable standard optimizations
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    with open(output_path, 'wb') as f:
        f.write(tflite_model)

    print(f"Exported TFLite model to: {output_path} ({len(tflite_model) / 1024:.2f} KB)")
    return output_path

def verify_tflite_model(tflite_path):
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    input_shape = input_details[0]['shape']
    input_type = input_details[0]['dtype']
    output_shape = output_details[0]['shape']
    output_type = output_details[0]['dtype']

    # Create synthetic input [1, 100, 6]
    dummy_input = np.random.randn(1, 100, 6).astype(np.float32)
    interpreter.set_tensor(input_details[0]['index'], dummy_input)
    interpreter.invoke()
    output_data = interpreter.get_tensor(output_details[0]['index'])

    return {
        "input_shape": input_shape.tolist(),
        "input_type": str(input_type),
        "output_shape": output_shape.tolist(),
        "output_type": str(output_type),
        "verification_passed": True,
        "sample_output_probabilities": output_data[0].tolist()
    }

def main():
    print("=== Step 1: Processing SisFall Dataset ===")
    X_train, y_train, X_val, y_val, X_test, y_test = process_dataset()

    total_windows = len(X_train) + len(X_val) + len(X_test)
    print(f"Total windows generated: {total_windows}")
    print(f"  Train windows: {len(X_train)} (ADL: {np.sum(y_train==0)}, Fall: {np.sum(y_train==1)})")
    print(f"  Val   windows: {len(X_val)} (ADL: {np.sum(y_val==0)}, Fall: {np.sum(y_val==1)})")
    print(f"  Test  windows: {len(X_test)} (ADL: {np.sum(y_test==0)}, Fall: {np.sum(y_test==1)})")

    print("\n=== Step 2: Computing Normalization Parameters (Train Only) ===")
    means, stds, norm_json = compute_and_save_normalization(X_train)
    print("Channel Means:", means)
    print("Channel Stds: ", stds)

    # Normalize datasets
    X_train_norm = apply_normalization(X_train, means, stds)
    X_val_norm = apply_normalization(X_val, means, stds)
    X_test_norm = apply_normalization(X_test, means, stds)

    print("\n=== Step 3: Building and Training 1D CNN Model ===")
    model = build_1d_cnn_model(input_shape=(100, 6))
    model.summary()

    callbacks = [
        tf.keras.callbacks.EarlyStopping(monitor='val_loss', patience=8, restore_best_weights=True)
    ]

    history = model.fit(
        X_train_norm, y_train,
        validation_data=(X_val_norm, y_val),
        epochs=40,
        batch_size=64,
        callbacks=callbacks,
        verbose=1
    )

    print("\n=== Step 4: Evaluating Model on Test Set ===")
    test_loss, test_acc = model.evaluate(X_test_norm, y_test, verbose=0)
    y_pred_probs = model.predict(X_test_norm, verbose=0)
    y_pred = np.argmax(y_pred_probs, axis=1)

    acc = accuracy_score(y_test, y_pred)
    prec = precision_score(y_test, y_pred)
    rec = recall_score(y_test, y_pred)
    f1 = f1_score(y_test, y_pred)
    cm = confusion_matrix(y_test, y_pred)

    print(f"Test Accuracy : {acc:.4f}")
    print(f"Test Precision: {prec:.4f}")
    print(f"Test Recall   : {rec:.4f}")
    print(f"Test F1 Score : {f1:.4f}")
    print("Confusion Matrix:\n", cm)

    print("\n=== Step 5: Exporting to TFLite ===")
    tflite_path = export_tflite(model, TFLITE_OUTPUT_PATH)

    print("\n=== Step 6: Verifying TFLite Model ===")
    tflite_info = verify_tflite_model(tflite_path)
    print("TFLite Verification Info:", json.dumps(tflite_info, indent=2))

    # --- Summary Report JSON ---
    summary_report = {
        "total_generated_windows": total_windows,
        "subjects": {
            "train": sorted(list(TRAIN_SUBJECTS)),
            "val": sorted(list(VAL_SUBJECTS)),
            "test": sorted(list(TEST_SUBJECTS))
        },
        "window_counts": {
            "train": len(X_train),
            "val": len(X_val),
            "test": len(X_test)
        },
        "normalization": norm_json,
        "metrics": {
            "test_accuracy": float(acc),
            "test_precision": float(prec),
            "test_recall": float(rec),
            "test_f1_score": float(f1),
            "confusion_matrix": cm.tolist()
        },
        "tflite": tflite_info,
        "tflite_location": tflite_path,
        "errors": []
    }

    report_path = os.path.join(OUTPUT_DIR, "training_summary_report.json")
    with open(report_path, "w") as f:
        json.dump(summary_report, f, indent=2)

    print(f"\nTraining pipeline completed successfully! Summary saved to {report_path}")

if __name__ == "__main__":
    main()
