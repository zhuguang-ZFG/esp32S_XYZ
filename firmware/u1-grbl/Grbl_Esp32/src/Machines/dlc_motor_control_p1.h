#pragma once
// clang-format off

/*
    dlc_motor_control_p1.h

    Board-specific machine definition for DLC_Motor_Control_P1_V1.0_260513.

    Hardware summary from schematic/PCB:
    - MCU: ESP32-S3 (U1 / MOTOR_MCU)
    - 4 external TMC2208 driver channels wired as:
        J5 -> X
        J1 -> Y
        J3 -> Y2
        J4 -> Z
    - 4 opto-isolated limit/home inputs wired as:
        UUT_SENSOR_1 -> X
        UUT_SENSOR_2 -> Y
        UUT_SENSOR_3 -> Y2
        UUT_SENSOR_4 -> Z
    - Shared motor enable:
        MOTOR_EN
    - Laser MOS control:
        LASER_CONTROL

    Notes:
    - This definition intentionally models the machine as XYYZ.
    - Y/Y2 are squared during homing by using two independent home inputs.
    - TMC2208 UART is not currently routed to U1 in the schematic, so this uses
      standard external step/dir driver mode rather than Trinamic UART mode.
*/

#define MACHINE_NAME "DLC Motor Control P1 XYYZ"

#define N_AXIS 3

#ifdef USE_RMT_STEPS
#    undef USE_RMT_STEPS
#endif

// Home Y/Y2 independently to square the gantry.
#define DEFAULT_HOMING_SQUARED_AXES (bit(Y_AXIS))

// Driver outputs.
#define X_STEP_PIN              GPIO_NUM_46
#define X_DIRECTION_PIN         GPIO_NUM_3

#define Y_STEP_PIN              GPIO_NUM_8
#define Y_DIRECTION_PIN         GPIO_NUM_18

#define Y2_STEP_PIN             GPIO_NUM_17
#define Y2_DIRECTION_PIN        GPIO_NUM_16

#define Z_STEP_PIN              GPIO_NUM_6
#define Z_DIRECTION_PIN         GPIO_NUM_5

// Shared enable line for all external drivers.
#define STEPPERS_DISABLE_PIN    GPIO_NUM_4

// Independent home / limit inputs.
#define X_LIMIT_PIN             GPIO_NUM_9
#define Y_LIMIT_PIN             GPIO_NUM_12
#define Y2_LIMIT_PIN            GPIO_NUM_13
#define Z_LIMIT_PIN             GPIO_NUM_14

// Optional future probe input is not assigned in this board revision.
// Pressure sensing is done through HX711 and is not wired into Grbl probe logic.

// Laser output through low-side MOSFET.
#define SPINDLE_TYPE            SpindleType::PWM
#define SPINDLE_OUTPUT_PIN      GPIO_NUM_45

#ifndef ENABLE_SOFTWARE_DEBOUNCE
#    define ENABLE_SOFTWARE_DEBOUNCE
#endif

// Motion defaults.
// These are conservative bring-up values and will likely need machine-specific tuning.
#define DEFAULT_STEP_PULSE_MICROSECONDS   4
// Release motor enable shortly after motion stops so XY can de-energize and
// the spring-loaded Z mechanism can return to pen-up when the motor is idle.
#define DEFAULT_STEPPER_IDLE_LOCK_TIME    25

#define DEFAULT_STEPPING_INVERT_MASK      0
#define DEFAULT_DIRECTION_INVERT_MASK     0
#define DEFAULT_INVERT_ST_ENABLE          0

// The sensor interface polarity is configurable in hardware. Start with non-inverted
// limit inputs and adjust via settings if field wiring/assembly uses opposite polarity.
#define DEFAULT_INVERT_LIMIT_PINS         0
#define DEFAULT_INVERT_PROBE_PIN          0

#define DEFAULT_STATUS_REPORT_MASK        1
#define DEFAULT_JUNCTION_DEVIATION        0.01
#define DEFAULT_ARC_TOLERANCE             0.002
#define DEFAULT_REPORT_INCHES             0

#define DEFAULT_SOFT_LIMIT_ENABLE         1
#define DEFAULT_HARD_LIMIT_ENABLE         1

#define DEFAULT_HOMING_ENABLE             1

// Home all axes toward their negative direction first.
#define DEFAULT_HOMING_DIR_MASK           (bit(X_AXIS) | bit(Y_AXIS) | bit(Z_AXIS))
#define DEFAULT_HOMING_FEED_RATE          100.0
#define DEFAULT_HOMING_SEEK_RATE          800.0
#define DEFAULT_HOMING_DEBOUNCE_DELAY     250
#define DEFAULT_HOMING_PULLOFF            2.0

// Run Z first, then X, then Y/Y2 together with squaring enabled.
#define DEFAULT_HOMING_CYCLE_0            (bit(Z_AXIS))
#define DEFAULT_HOMING_CYCLE_1            (bit(X_AXIS))
#define DEFAULT_HOMING_CYCLE_2            (bit(Y_AXIS))

// Keep machine locked until homing is completed after reset.
#ifndef HOMING_INIT_LOCK
#    define HOMING_INIT_LOCK
#endif

// Enable dual-switch squaring support on ganged axes.
#define LIMITS_TWO_SWITCHES_ON_AXES       1

// Laser defaults.
#define DEFAULT_SPINDLE_RPM_MIN           0.0
#define DEFAULT_SPINDLE_RPM_MAX           1000.0
#define DEFAULT_LASER_MODE                1

// Initial motion scaling defaults.
// Update these after mechanical calibration.
#define DEFAULT_X_STEPS_PER_MM            80.0
#define DEFAULT_Y_STEPS_PER_MM            80.0
#define DEFAULT_Z_STEPS_PER_MM            400.0

#define DEFAULT_X_MAX_RATE                6000.0
#define DEFAULT_Y_MAX_RATE                6000.0
#define DEFAULT_Z_MAX_RATE                1500.0

#define DEFAULT_X_ACCELERATION            200.0
#define DEFAULT_Y_ACCELERATION            200.0
#define DEFAULT_Z_ACCELERATION            80.0

#define DEFAULT_X_MAX_TRAVEL              300.0
#define DEFAULT_Y_MAX_TRAVEL              300.0
#define DEFAULT_Z_MAX_TRAVEL              80.0
