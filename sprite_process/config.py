
IMG_DIM: int = 4096
IMG_CENTER: int = 2048

BODY_FRAMES = (12, 48)
ARMS_FRAMES = (49, 61)
ARMS_SWAT_FRAMES = (62, 86)

# we move the arm connection joint outwards from the center of the body by this
# many pixels, for both arms
ARM_JOINT_FUDGE_X = 30
ARM_JOINT_FUDGE_Y = 0

TRACKERS_CACHE_FILE = "cached_trackers.pickle"

def out_img_path_for(fname: str) -> str:
    return f"../graphics/HaxMech/ships/g1/{fname}"

def head_img_name() -> str:
    return "img_in/girlhead.png"

def head_normalmap_img_name() -> str:
    return "img_in/girlhead_canvases/girlhead-Canvas2-Normal.png"

def arms_img_name(frame: int) -> str:
    return f"img_in/girlparms{str(frame).zfill(3)}.png"

def torso_img_name(frame: int) -> str:
    return f"img_in/girltorso{str(frame).zfill(3)}.png"

def waist_img_name(frame: int) -> str:
    return f"img_in/girlwaist{str(frame).zfill(3)}.png"

def legs_img_name(frame: int) -> str:
    return f"img_in/girlplegs{str(frame).zfill(3)}.png"

def spinetracker_img_name(frame: int) -> str:
    return f"img_in/spinetracker{str(frame).zfill(3)}.png"

def shouldertracker_img_name(frame: int) -> str:
    return f"img_in/shouldertracker{str(frame).zfill(3)}.png"

def pelvistracker_img_name(frame: int) -> str:
    return f"img_in/pelvistracker{str(frame).zfill(3)}.png"

def foottracker_img_name(frame: int) -> str:
    return f"img_in/foottracker{str(frame).zfill(3)}.png"

def handtracker_img_name(frame: int) -> str:
    return f"img_in/handtracker{str(frame).zfill(3)}.png"
