"""Shared, reference-led concept geometry for glTF preview and Minecraft voxelization.

Coordinates are Minecraft blocks. All parts are positive solids; the entry opening
is built from pillars and arch segments, so the preview and NBT share its clearance.
"""
import math

SIZE=(130,72,162)
DOOR=(65,27,56)
RETURN=(65,25,66)
STEPS=tuple(range(138,123,-2))+tuple(range(112,97,-2))+tuple(range(86,71,-2))
MATERIALS={
 'cream':('smooth_sandstone','#ddca91'), 'gold':('cut_sandstone','#c6a354'),
 'dark':('brown_terracotta','#6d4a2d'), 'brick':('red_terracotta','#9c6653'),
 'green':('green_terracotta','#4c7c3f'), 'glass':('cyan_stained_glass','#4b99a3'),
 'white':('smooth_quartz','#ebe7d8'), 'stone':('stone_bricks','#92978a'),
 'grass':('grass_block','#7eac69'), 'leaf':('oak_leaves','#397b3c'),
 'leaf_light':('azalea_leaves','#599646'), 'trunk':('oak_log','#725039'),
 'pink':('pink_tulip','#e8adc0'), 'flower':('white_tulip','#f2edd9'),
 'light':('sea_lantern','#d7f2da'), 'barrier':('barrier','#70a7aa'),
}

def terrace_height(z): return sum(z < step for step in STEPS)


def garden_height(x,z):
    """Three nested retaining terraces; the central stair has its own slope."""
    return sum(8 for margin,end in ((4,140),(12,114),(20,88))
               if margin <= x < 130-margin and margin <= z < end)

def scene():
    parts=[]
    def box(mat,x,y,z,w,h,d,group='building',props=None):
        if group=='terrain' and z+d>58 and x<78 and x+w>52:
            if z<58: box(mat,x,y,z,w,h,58-z,group,props)
            start=max(58,z)
            if x<52: box(mat,x,y,start,52-x,h,z+d-start,group,props)
            if x+w>78: box(mat,78,y,start,x+w-78,h,z+d-start,group,props)
            return
        if group in ('building','portal'): y+=12
        parts.append(dict(kind='box',material=mat,origin=[x,y,z],size=[w,h,d],group=group,props=props or {}))
    def prism(mat,poly,z,depth,group='building'):
        if group=='building': poly=[(x,y+12) for x,y in poly]
        parts.append(dict(kind='prism',material=mat,polygon=poly,z=z,depth=depth,group=group))
    def ellipsoid(mat,x,y,z,rx,ry,rz):
        parts.append(dict(kind='ellipsoid',material=mat,center=[x,y,z],radii=[rx,ry,rz],group='garden'))
    # Nested terraces wrap all four elevations, as in the approved turnaround.
    box('grass',0,0,0,130,1,162,'terrain',{'snowy':'false'})
    for level,(margin,end) in enumerate(((4,140),(12,114),(20,88))):
        box('stone',margin,level*8+1,margin,130-2*margin,8,end-margin,'terrain')
        box('grass',margin,level*8+8,margin,130-2*margin,1,end-margin,'terrain',{'snowy':'false'})
        # Continuous capstone outlines on the sides and rear of each level.
        for x in (margin,129-margin):
            box('cream',x,level*8+9,margin,1,1,end-margin,'terrain')
        box('cream',margin,level*8+9,margin,130-2*margin,1,1,'terrain')
        for x,w in ((margin,50-margin),(80,50-margin)):
            box('cream',x,level*8+9,end-1,w,1,1,'terrain')
    for z in range(162):
        h=terrace_height(z)
        if z>=58:
            box('white',52,0,z,26,h+1,1,'stairs')
            box('white',52,h,z,26,1,1,'stairs')
        if z in STEPS:
            box('white',52,h+1,z,26,1,1,'stairs',{'block':'quartz_stairs','facing':'north','half':'bottom','shape':'straight','waterlogged':'false'})
    box('cream',24,12,22,82,1,40)
    # Open vestibule between the two wings. The broad stepped pyramid is removed.
    box('brick',30,13,24,70,24,3)
    box('brick',28,13,27,3,24,26)
    box('brick',99,13,27,3,24,26)
    # Both side walls and the rear use real window recesses and framed pilasters.
    for x in (27,100):
        for z in (28,36,44,51):
            box('cream',x-1,13,z,4,2,3)
            box('cream',x,15,z,2,19,2)
            box('gold',x-1,34,z-1,4,2,4)
        for z in (32,40,48):
            box('dark',x,18,z,2,13,2)
            box('glass',x-.2 if x<65 else x+.2,19,z,2,11,1)
    for x in range(39,93,9):
        box('cream',x,13,22,3,2,4)
        box('cream',x+1,15,23,1,19,2)
        box('gold',x,34,22,3,2,4)
        if x != 66:
            box('dark',x+4,18,23,3,13,1)
            box('glass',x+5,19,22,1,11,1)
    box('cream',61,20,22,9,12,1)
    box('brick',63,22,21,5,8,1)
    for x,y in ((65,29),(64,28),(66,28),(63,27),(67,27),(64,26),(66,26),(65,25)):
        box('gold',x,y,20,1,1,1)
    for y,mat in ((14,'cream'),(33,'green'),(35,'gold')):
        box(mat,27,y,23,76,1,2)
        for x in (27,101): box(mat,x,y,25,2,1,29)
    for x in (31,78):
        box('brick',x,13,50,21,23,3)
        for xx in (x,x+7,x+16):
            box('gold',xx,14,53,2,21,2)
        for y in (15,28,35): box('cream',x,y,53,21,1,2)
    # Low pitched roof with slim eaves and a high central pediment.
    prism('gold',[(27,36),(103,36),(95,40),(75,43),(55,43),(35,40)],14,39)
    prism('cream',[(26,36),(104,36),(104,37),(76,44),(54,44),(26,37)],13,41)
    prism('gold',[(31,38),(55,44),(75,44),(99,38),(94,41),(75,46),(55,46),(36,41)],16,34)
    # Slender squared towers: vertical red panels, restrained bands and small caps.
    for x in (25,92):
        for z in (23,44):
            box('cream',x,13,z,13,36,12)
            for zz in (z-1,z+12): box('brick',x+3,16,zz,7,28,1)
            for xx in (x-1,x+13): box('brick',xx,16,z+3,1,28,6)
            for y in (15,30,43,49):
                box('gold',x-1,y,z-1,15,1,14)
                box('cream',x-2,y+1,z-2,17,1,16)
            box('green',x-1,47,z-1,15,1,14)
            box('cream',x-1,51,z-1,15,1,14)
    # Tall recessed pointed doorway, gold stone frame and emerald tympanum.
    for x in (49,77):
        box('green',x-1,13,52,6,2,10)
        box('gold',x,15,53,4,19,8)
        box('cream',x+1,15,61,2,17,1)
        box('green',x-1,32,52,6,2,10)
    prism('gold',[(48,33),(48,38),(61,45),(69,45),(82,38),(82,33),(77,33),(65,40),(53,33)],51,10)
    # Arch segments form the upper edge without filling the walk-through opening.
    for x in range(54,76):
        top=34-abs(x+.5-65)*.52
        prism('cream',[(x,top),(x+1,top-.52 if x>=65 else top+.52),(x+1,top+3),(x,top+3)],57,3)
    prism('green',[(55,34),(65,40),(75,34)],60,1)
    # Three gold bands descend beside the entrance, echoing the garden screens.
    for mirrored in (False,True):
        for y in (32,35,38):
            poly=[(39,y),(47,y),(56,y+5),(56,y+3),(48,y-2),(39,y-2)]
            if mirrored: poly=[(130-x,yy) for x,yy in poly]
            prism('gold',poly,60,3)
    box('glass',58,13,53,14,17,1)
    # A small crest on the glass; no giant unrelated Pokeball pasted onto the roof.
    for x,y in ((65,25),(64,24),(66,24),(63,23),(67,23),(64,22),(66,22),(65,21)):
        box('light',x,y,54,1,1,1)
    for side in (-1,1):
        for n in range(3):
            for band in (0,2):
                box('white',65+side*(3+n),24+n-band,54,1,1,1)
    box('barrier',60,13,56,10,6,2,'portal')
    box('light',58,31,54,14,1,3)
    # Side ceremonial screens. Crucially, these do NOT bridge across the stair.
    for z,base in ((94,16),(146,0)):
        for mirrored in (False,True):
            def mx(x): return 130-x if mirrored else x
            def poly(points): return [(mx(x),base+y) for x,y in points]
            left,right=(34,49) if not mirrored else (81,96)
            box('green',left,base+1,z,15,2,7,'gates')
            for xx in (left+1,right-3):
                box('gold',xx,base+3,z+1,3,13,5,'gates')
                box('cream',xx+1,base+3,z+6,1,11,1,'gates')
            # Three narrow gold stripes with the raised inner corner seen in LGPE.
            for y in (11,14,17):
                prism('gold',poly([(32,y),(43,y),(50,y+6),(51,y+5),(44,y-2),(32,y-2)]),z,7,'gates')
                prism('cream',poly([(32,y),(43,y),(50,y+6),(51,y+5.3),(43.4,y-.8),(32,y-.8)]),z-.3,7.6,'gates')
            box('green',left-1,base+8,z-1,17,2,9,'gates')
    # Low stair rails keep the middle open; no massive overhead lintels.
    for z in range(68,151):
        h=terrace_height(z)
        for x in (50,78):
            box('cream',x,h+1,z,2,1,1,'stairs')
            box('gold',x,h+2,z,2,.5,1,'stairs')
    # Compact cubic crowns keep the four elevations visible above the gardens.
    trees=[(x,z) for x in (8,16,114,122) for z in (32,61,80)]
    trees += [(x,z) for x in (29,44,86,101) for z in (79,105,135)]
    trees += [(x,8) for x in (32,50,80,98)]
    for x,z in trees:
        h=garden_height(x,z)
        box('trunk',x,h+1,z,1,6,1,'garden',{'axis':'y'})
        box('leaf',x-2,h+5,z-2,5,3,5,'garden')
        box('leaf_light',x-1,h+8,z-1,3,2,3,'garden')
    for margin,end in ((4,140),(12,114),(20,88)):
        # Flower borders just inside each retaining wall.
        coords={(x,end-3) for x in range(margin+3,130-margin-3,2) if x<49 or x>80}
        coords |= {(x,z) for x in (margin+2,127-margin) for z in range(margin+3,end-4,3)}
        coords |= {(x,margin+2) for x in range(margin+3,127-margin,3)}
        for x,z in coords:
            box('pink' if (x+z)%2 else 'flower',x,garden_height(x,z)+1,z,1,1,1,'garden')
        # Lamps repeat on the terrace corners, independently of the stair slope.
        for x in (margin+1,128-margin):
            for z in (margin+1,end-2):
                h=garden_height(x,z)
                box('cream',x,h+1,z,1,2,1,'garden')
                box('light',x,h+3,z,1,1,1,'garden')
    for z in (69,91,117,151):
        for x in (50,79):
            h=terrace_height(z)
            box('cream',x,h+1,z,1,3,1,'stairs')
            box('light',x,h+4,z,1,1,1,'stairs')
    # Two-block human scale reference is preview-only, never an NPC in the NBT.
    box('dark',68,terrace_height(150)+1,150,.6,1.8,.6,'scale')
    return parts
