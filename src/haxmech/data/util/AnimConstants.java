package haxmech.data.util;

import com.fs.starfarer.api.util.IntervalUtil;

public class AnimConstants {
    // number of frames in the body animation
    public static int N_BODY_FRAMES = 37;
    public static int MID_BODY_FRAME = 18;
    // The maximum angle between the legs and body
    public static float LEGS_MAX_LAG_ANGLE = 60f;
    // the hip/waist rotation will tend to straighten out naturally over time
    // Higher is more damping.
    public static float LEGS_ANGLE_DAMP_CONSTANT = 0.4f;
    // the legs "lag point" has its own position and velocity,
    // which is influenced by the distance from the body as if it were a spring.
    // this is the spring constant for that spring.
    // Higher is more damping.
    public static float LEGS_DIST_DAMP_CONSTANT = 0.1f;
    // damp the velocity of the spring legs so they don't turbo-oscillate all the time.
    // Higher is more damping.
    public static float LEGS_VEL_DAMP_CONSTANT = 0.1f;

    public static float ARM_L_SWAT_START = 170f;
    public static float ARM_L_SWAT_END = 10f;

    public static float ARM_R_SWAT_START = 10f;
    public static float ARM_R_SWAT_END = 170f;


    static int ARM_FIRE_FRAME_OFFSET = 0;
    static int N_ARM_FIRE_FRAMES = 13;

    static int ARM_SWAT_FRAME_OFFSET = 13;
    static int ARM_SWAT_FRAME_SETUP_END = 10;
    static int ARM_SWAT_FRAME_SWAT_END = 14;
    static int N_ARM_SWAT_FRAMES = 25;
}
