from typing import NamedTuple, Dict

import math
import pickle 

from PIL import Image

from img_util import AABB, Point2, \
    aabb_union, aabb_intersect, size_with_aabb_and_center, \
    calc_intersect_center, recenter_with_aabb, draw_debug_dot, \
    auto_white_balance
from ship_gen.ship import gen_hull_json, gen_variant_json, \
    gen_wpn_json
from config import spinetracker_img_name, arms_img_name, \
    legs_img_name, torso_img_name, waist_img_name, \
    shouldertracker_img_name, out_img_path_for, head_img_name, \
    head_normalmap_img_name, pelvistracker_img_name, foottracker_img_name, \
    handtracker_img_name, \
    TRACKERS_CACHE_FILE, IMG_DIM, BODY_FRAMES, ARMS_FRAMES, ARMS_SWAT_FRAMES, \
    ARM_JOINT_FUDGE_X

# map from legtorso frame to vertical offset to apply to shoulder module. We
# emit this as constants for Java later
FrameOffsetMap = list[int]

def aabb_for(fname: str) -> AABB:
    with Image.open(fname) as im:
        bbox = im.getbbox()
        if bbox is None:
            raise Exception("No bbox!")
        return AABB(bbox[0], bbox[1], bbox[2], bbox[3])

def aabb_center(fname: str) -> Point2:
    aabb = aabb_for(fname)
    return Point2(
        round((aabb.x0 + aabb.x1) / 2),
        round((aabb.y0 + aabb.y1) / 2)
    )

class JointOffsets(NamedTuple):
    """
    These offsets are all indexed by body frame. i.e. shoulder offset 0 belongs
    to frame 0 of the body animation, and frame 12 of the "raw" animation.
    """

    # shoulder, pelvis, spine offsets are from the first frame's position (i.e.
    # the base offset)
    shoulder_offsets: FrameOffsetMap
    shoulder_base_offset: int

    pelvis_offsets: FrameOffsetMap
    pelvis_base_offset: int

    spine_offsets: FrameOffsetMap
    spine_base_offset: int

    # foot offsets are from pelvis
    foot_offsets: FrameOffsetMap

    torso_aabb: AABB
    waist_aabb: AABB
    legs_aabb: AABB

class AllTrackers(NamedTuple):
    """
    These are all indexed in the "raw" frames.
    """
    shouldertrackers: dict[int, Point2]
    spinetrackers: dict[int, Point2]
    pelvistrackers: dict[int, Point2]
    foottrackers: dict[int, Point2]
    handtrackers: dict[int, Point2]

def get_trackers() -> AllTrackers:
    # this caches
    try:
        with open(TRACKERS_CACHE_FILE, 'rb') as tf:
            return pickle.load(tf)
    except:
        all_spinetrackers: dict[int, Point2] = {}
        all_shouldertrackers: dict[int, Point2] = {}
        all_pelvistrackers: dict[int, Point2] = {}
        all_foottrackers: dict[int, Point2] = {}
        all_handtrackers: dict[int, Point2] = {}

        for i in range(12, max(
            BODY_FRAMES[1],
            ARMS_FRAMES[1],
            ARMS_SWAT_FRAMES[1]
        )+1):
            print("tracker", i)
            all_shouldertrackers[i] = aabb_center(shouldertracker_img_name(i))
            if i >= BODY_FRAMES[0] and i <= BODY_FRAMES[1]:
                all_spinetrackers[i] = aabb_center(spinetracker_img_name(i))
                all_pelvistrackers[i] = aabb_center(pelvistracker_img_name(i))
                all_foottrackers[i] = aabb_center(foottracker_img_name(i))
                all_handtrackers[i] = aabb_center(handtracker_img_name(i))

        ret = AllTrackers(
            shouldertrackers=all_shouldertrackers,
            spinetrackers=all_spinetrackers,
            pelvistrackers=all_pelvistrackers,
            foottrackers=all_foottrackers,
            handtrackers=all_handtrackers)

        with open(TRACKERS_CACHE_FILE, 'wb') as tf:
            pickle.dump(ret, tf)

        return ret

def process_body(all_trackers: AllTrackers) -> JointOffsets:
    torso_aabb = AABB(1000000, 1000000, 0, 0)
    legs_aabb = AABB(1000000, 1000000, 0, 0)
    waist_aabb = AABB(1000000, 1000000, 0, 0)

    all_torso_aabbs: list[AABB] = []
    all_waist_aabbs: list[AABB] = []
    all_legs_aabbs: list[AABB] = []

    for i in range(BODY_FRAMES[1] - BODY_FRAMES[0] + 1):
        print("body AABB calculating", i)

        this_torso_aabb = aabb_for(torso_img_name(i + BODY_FRAMES[0]))
        all_torso_aabbs.append(this_torso_aabb)
        torso_aabb = aabb_union(torso_aabb, this_torso_aabb)

        this_waist_aabb = aabb_for(waist_img_name(i + BODY_FRAMES[0]))
        all_waist_aabbs.append(this_waist_aabb)
        waist_aabb = aabb_union(waist_aabb, this_waist_aabb)

        this_legs_aabb = aabb_for(legs_img_name(i + BODY_FRAMES[0]))
        all_legs_aabbs.append(this_legs_aabb)
        legs_aabb = aabb_union( legs_aabb, this_legs_aabb)

    print("AABBs:", torso_aabb, waist_aabb, legs_aabb)

    shoulder_offset_map: list[int] = []
    shoulder_base_offset = 0

    pelvis_offset_map: list[int] = []
    pelvis_base_offset = 0

    spine_offset_map: list[int] = []
    spine_base_offset = 0

    foot_offset_map: list[int] = []

    for i in range(BODY_FRAMES[1] - BODY_FRAMES[0] + 1):
        print("body generating", i)
        this_pelvistracker = all_trackers.pelvistrackers[i + BODY_FRAMES[0]]
        this_spinetracker = all_trackers.spinetrackers[i + BODY_FRAMES[0]]

        this_shoulder_offset = all_trackers.shouldertrackers[i + BODY_FRAMES[0]].y
        this_spine_offset = this_spinetracker.y
        this_pelvis_offset = this_pelvistracker.y
        this_foot_offset = all_trackers.foottrackers[i + BODY_FRAMES[0]].y

        process_body_frame(
            i + BODY_FRAMES[0],
            i,
            torso_aabb, waist_aabb, legs_aabb,
            this_spinetracker, this_pelvistracker)

        # implicitly negate all of these values, as PIL's coordinates are Y+
        # down, whereas starsector is Y+ up
        if len(shoulder_offset_map) == 0:
            shoulder_base_offset = this_shoulder_offset
        shoulder_offset_map.append(shoulder_base_offset - this_shoulder_offset)

        if len(spine_offset_map) == 0:
            spine_base_offset = this_spine_offset
        spine_offset_map.append(spine_base_offset - this_spine_offset)

        if len(pelvis_offset_map) == 0:
            pelvis_base_offset = this_pelvis_offset
        pelvis_offset_map.append(pelvis_base_offset - this_pelvis_offset)

        if len(foot_offset_map) == 0:
            foot_base_offset = this_foot_offset
        foot_offset_map.append(this_pelvis_offset - this_foot_offset)

    return JointOffsets(
        shoulder_offsets=shoulder_offset_map,
        shoulder_base_offset=shoulder_base_offset,
        pelvis_offsets=pelvis_offset_map,
        pelvis_base_offset=pelvis_base_offset,
        spine_offsets=spine_offset_map,
        spine_base_offset=spine_base_offset,
        foot_offsets=foot_offset_map,
        torso_aabb=torso_aabb, 
        waist_aabb=waist_aabb,
        legs_aabb=legs_aabb
        )

def process_body_frame(
        frame: int, out_frame: int,
        torso_aabb: AABB, waist_aabb: AABB, legs_aabb: AABB,
        spinetracker: Point2, pelvistracker: Point2):
    """
    Process 1 frame of torso/leg sprite data.

    Returns the vertical offset of the center of the shoulder, as defined by
    averaging all existent pixel positions at each shoulder X position.
    """
    with Image.open(torso_img_name(frame)) as torso_img:
        with Image.open(waist_img_name(frame)) as waist_img:
            with Image.open(legs_img_name(frame)) as legs_img:
                assert(torso_img.size == legs_img.size)
                im_width, im_height = torso_img.size

                im_center = Point2(
                    math.floor(im_width / 2),
                    math.floor(im_height / 2),
                )

                #draw_debug_dot(legs_img, (spinetracker.x, spinetracker.y))
                #draw_debug_dot(torso_img, (spinetracker.x, spinetracker.y))
                #draw_debug_dot(torso_img, (shouldertracker.x, shouldertracker.y))

                new_torso_img = recenter_with_aabb(torso_img, torso_aabb, im_center)
                new_waist_img = recenter_with_aabb(waist_img, waist_aabb, spinetracker)
                new_legs_img = recenter_with_aabb(legs_img, legs_aabb, pelvistracker)

                new_torso_img.save(out_img_path_for(f"girltorso{str(out_frame).zfill(2)}.png"))
                new_waist_img.save(out_img_path_for(f"girlwaist{str(out_frame).zfill(2)}.png"))
                new_legs_img.save(out_img_path_for(f"girllegs{str(out_frame).zfill(2)}.png"))

def process_head():
    with Image.open(head_img_name()) as head_img:
        with Image.open(head_normalmap_img_name()) as head_normal_img:
            head_aabb = AABB.from_4tuple(head_img.getbbox())

            print("HEAD AABB", head_aabb)

            im_center = Point2(
                math.floor(head_img.width / 2),
                math.floor(head_img.height / 2),
            )

            new_head_img = recenter_with_aabb(head_img, head_aabb, im_center)
            new_head_normal_img = recenter_with_aabb(head_normal_img, head_aabb, im_center)

            # our normal map by default doesn't have transparency
            new_head_normal_img.putalpha(new_head_img.getchannel("A"))

            new_head_img.save(out_img_path_for("girlhead.png"))
            new_head_normal_img.save(out_img_path_for("girlhead_normal.png"))

def process_arms_frame(
        frame: int,
        out_frame: int, 
        arms_aabb: AABB, 
        shouldertracker: Point2):

    with Image.open(arms_img_name(frame)) as arms_img:
        larm_img = arms_img.copy()
        larm_img.paste(
            (0,0,0,0),
            (math.floor(arms_img.width/2), 0, arms_img.width, arms_img.height))

        rarm_img = arms_img.copy()
        rarm_img.paste(
            (0,0,0,0),
            (0, 0, math.floor(arms_img.width/2), arms_img.height))

        # this arms/shoulders should be symmetrical
        larm_socket_center = Point2(shouldertracker.x + ARM_JOINT_FUDGE_X, shouldertracker.y)
        rarm_socket_center = Point2(IMG_DIM - shouldertracker.x - ARM_JOINT_FUDGE_X, shouldertracker.y)
        #larm_socket_center = Point2(shouldertracker.x, shouldertracker.y)
        #rarm_socket_center = Point2(IMG_DIM - shouldertracker.x, shouldertracker.y)

        #draw_debug_dot(larm_img, larm_socket_center)
        #draw_debug_dot(rarm_img, rarm_socket_center)

        new_larm_img = recenter_with_aabb(larm_img, arms_aabb, larm_socket_center)
        new_rarm_img = recenter_with_aabb(rarm_img, arms_aabb, rarm_socket_center)

        new_larm_img.save(out_img_path_for(f"girlarm_l{str(out_frame).zfill(2)}.png"))
        new_rarm_img.save(out_img_path_for(f"girlarm_r{str(out_frame).zfill(2)}.png"))

def process_arms(all_shouldertrackers: dict[int, Point2]) -> AABB:
    arms_aabb = AABB(1000000, 1000000, 0, 0)

    for i in range(ARMS_FRAMES[1] - ARMS_FRAMES[0] + 1):
        print("arms AABB calculating", i, i + ARMS_FRAMES[0])
        arms_aabb = aabb_union(
            arms_aabb,
            aabb_for(arms_img_name(i + ARMS_FRAMES[0])))
    for i in range(ARMS_SWAT_FRAMES[1] - ARMS_SWAT_FRAMES[0] + 1):
        print("armsswat AABB calculating", i, i + ARMS_SWAT_FRAMES[0])
        arms_aabb = aabb_union(
            arms_aabb,
            aabb_for(arms_img_name(i + ARMS_SWAT_FRAMES[0])))

    # pick the first torso frame to calculate "socket" placement against
    for i in range(ARMS_FRAMES[1] - ARMS_FRAMES[0] + 1):
        print("arms generating", i, i + ARMS_FRAMES[0])
        process_arms_frame(
            i + ARMS_FRAMES[0],
            i,
            arms_aabb,
            all_shouldertrackers[i + ARMS_FRAMES[0]])
    for i in range(ARMS_SWAT_FRAMES[1] - ARMS_SWAT_FRAMES[0] + 1):
        print("armsswat generating", i, i + ARMS_SWAT_FRAMES[0])
        process_arms_frame(
            i + ARMS_SWAT_FRAMES[0],
            i + 13,
            arms_aabb,
            all_shouldertrackers[i + ARMS_FRAMES[0]])

    return arms_aabb


def generate_blanksprites():
    new_big_blank_img = Image.new("RGBA", (500,500), (0,0,0,0))
    new_big_blank_img.save(out_img_path_for("blank500.png"))

    new_small_blank_img = Image.new("RGBA", (50,50), (0,0,0,0))
    new_small_blank_img.save(out_img_path_for("blank50.png"))

    new_big_red_img = Image.new("RGBA", (500,500), (255,0,0,255))
    new_big_red_img.save(out_img_path_for("red500.png"))

    new_small_red_img = Image.new("RGBA", (50,50), (255,0,0,255))
    new_small_red_img.save(out_img_path_for("red50.png"))

def gen_constants_file(
    lt_offsets: JointOffsets,
    arms_aabb: AABB,
):
    arm_length = round((arms_aabb.y1 - arms_aabb.y0) / 2 * 0.95)
    leg_length = round((lt_offsets.legs_aabb.y1 - lt_offsets.legs_aabb.y0) / 2 * 0.95)
    with open("../src/haxmech/data/util/AutogenFrameData.java", "w") as wf:
        wf.write("package haxmech.data.util;\n")
        wf.write("public class AutogenFrameData {\n")
        wf.write (
            "  public static final int ARM_LENGTH = %d;\n" % (
                arm_length
            ,))
        wf.write (
            "  public static final int LEG_LENGTH = %d;\n" % (
                leg_length
            ,))
        wf.write(
            "  public static final int[] FRAME_SHOULDER_OFFSETS = {%s};\n" % (
                ",".join(str(x) for x in lt_offsets.shoulder_offsets)
            ,))
        wf.write(
            "  public static final int[] FRAME_SPINE_OFFSETS = {%s};\n" % (
                ",".join(str(x) for x in lt_offsets.spine_offsets)
            ,))
        wf.write(
            "  public static final int[] FRAME_PELVIS_OFFSETS = {%s};\n" % (
                ",".join(str(x) for x in lt_offsets.pelvis_offsets)
            ,))
        wf.write(
            "  public static final int[] FRAME_FOOT_OFFSETS = {%s};\n" % (
                ",".join(str(x) for x in lt_offsets.foot_offsets)
            ,))

        wf.write("}\n")

trackers = get_trackers()
#print("trackers", trackers)

generate_blanksprites()
process_head()
lt_offsets = process_body(trackers)
arms_aabb = process_arms(trackers.shouldertrackers)

gen_hull_json(lt_offsets, trackers)
gen_variant_json()
gen_wpn_json(arms_aabb)

gen_constants_file(lt_offsets, arms_aabb)
