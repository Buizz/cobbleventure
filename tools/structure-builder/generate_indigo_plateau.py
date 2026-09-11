"""Explicit NBT authoring from the shared Indigo Plateau 3D concept model.

Normal builds package the saved NBT; they never overwrite hand edits.
"""
from pathlib import Path
import argparse
import json
import sys
from indigo_plateau_model import SIZE, DOOR, RETURN, STEPS, terrace_height
from compile_indigo_model import voxelize

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/mod-builder'))
from starter_gym import _build_structure_nbt
RESOURCE='cobbleventure:league/indigo_plateau'
OUTPUT=ROOT/'content-projects/cobbleventure-main/content/structures/league/indigo_plateau.nbt'

def layout(): return voxelize()

def generate(overwrite=False):
    if OUTPUT.exists() and not overwrite:
        raise SystemExit('Already authored. Use --overwrite only to replace the current design.')
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    OUTPUT.write_bytes(_build_structure_nbt(*layout()))
    metadata={'schema_version':1,'structure':'content/structures/league/indigo_plateau.nbt','anchors':[
        {'id':'door','label':'door','type':'transition','position':list(DOOR),'safe_spawn':list(RETURN),'facing':'south'}]}
    OUTPUT.with_suffix('.structure.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(OUTPUT)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--overwrite',action='store_true')
    generate(parser.parse_args().overwrite)
