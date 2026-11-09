#!/usr/bin/env python3
"""Versioned .73 visual-only companion. No historical guard/hash is replaced."""
from pathlib import Path
import hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[1]
FIX=ROOT/'src/test/resources/wind-pressure-flow/v72'
def sha(b):return hashlib.sha256(b).hexdigest()
def require(c,m):
 if not c:raise AssertionError(m)
FROZEN_FILES={'class-hashes.json': '78eedf1a35308defa11a4a0f88524ebc01e3a2bcd818bb9bded66f71536846e0', 'WindPressureLiftEffects.java.txt': '69b87574f8fc61d06abf86aff23245ddf4057c8bcde61b4229da07fe9da83ddd', 'VanillaCombatContract.java.txt': '41737f52101063f0d572e2544b1615b6d04dd6eb578f6ce345d5d92e2d6a6f32', 'VanillaMiningContract.java.txt': 'df0cb7d9e51a2d4754dd9f8f61b7f98572d87761924b5775e38df1404cb44c64'}
for name,expected in FROZEN_FILES.items():require(sha((FIX/name).read_bytes())==expected,"Frozen .72 evidence changed: "+name)
base=(FIX/'WindPressureLiftEffects.java.txt').read_bytes()
current=(ROOT/'src/main/java/cn/academy/port/client/WindPressureLiftEffects.java').read_bytes()
hook=b'        if (WindPressureLiftFlowRenderer.render(visible, event, partial)) return;\n'
require(current.count(hook)==1 and current.replace(hook,b'')==base,'OFF existing source must be byte-exact after sole optional hook removal')
require(current.replace(hook,b'').replace(b'Pass.SOFT',b'Pass.CORE',1)!=base,'OFF mutation negative control')
for name in ('VanillaCombatContract','VanillaMiningContract'):
 before=(FIX/(name+'.java.txt')).read_bytes();after=(ROOT/('src/main/java/cn/academy/port/highpower/'+name+'.java')).read_bytes()
 require(after.replace(b'0.1.0-m41-highpower.0.73-prototype',b'0.1.0-m41-highpower.0.72-prototype')==before,name+' may change only exact reviewed artifact version')
manifest=json.loads((FIX/'class-hashes.json').read_text())
allowed={'cn/academy/port/HighPowerVisualConfig.class','cn/academy/port/client/WindPressureLiftEffects.class',
 'cn/academy/port/client/WindPressureLiftEffects$Material.class','cn/academy/port/client/WindPressureLiftEffects$Pass.class',
 'cn/academy/port/highpower/VanillaCombatContract.class','cn/academy/port/highpower/VanillaMiningContract.class'}
jar=ROOT/'build/libs/academycraft-reborn-highpower-0.1.0-m41-highpower.0.73-prototype.jar'
with zipfile.ZipFile(jar) as z:
 actual={n:sha(z.read(n)) for n in z.namelist() if n.endswith('.class')}
 changed={n for n,h in manifest.items() if actual.get(n)!=h}
 require(changed==allowed,'Only reviewed render/config/version classes may differ: '+str(changed))
 require(all(n.startswith('cn/academy/port/client/WindPressureLiftFlow') for n in actual.keys()-manifest.keys()),'No new server or packet classes')
 for n in ('cn/academy/port/highpower/WindPressureLift.class','cn/academy/port/client/WindPressureLiftClientMotion.class','cn/academy/port/client/WindPressureLiftNativeTravel.class'):
  require(actual[n]==manifest[n],'Movement/authority unchanged '+n)
 corrupt=dict(actual);key='cn/academy/port/highpower/WindPressureLift.class';corrupt[key]='0'*64
 require(key in {n for n,h in manifest.items() if corrupt.get(n)!=h}-allowed,'Server mutation is rejected')
 textures={'effects/tornado_ring.png':'9f5cc41dd8e4464e441d0a6e0636f0042b8d3bee82a0ab7b2ca094b5414a6429','effects/glow_line.png':'0785f30721bd1281cb356452c1c68ae721d3b09304df5b23de05578593dbf361'}
 for n,h in textures.items():require(sha(z.read('assets/academy/textures/'+n))==h,'Original texture changed')
material=(ROOT/'src/main/java/cn/academy/port/client/WindPressureLiftFlowMaterial.java').read_text()
renderer=(ROOT/'src/main/java/cn/academy/port/client/WindPressureLiftFlowRenderer.java').read_text()
config=(ROOT/'src/main/java/cn/academy/port/HighPowerVisualConfig.java').read_text()
require('.define("windPressureLiftFlow", false)' in config,'Default OFF')
for gate in ['ready &&','forceFallback','softShader != null','SPEC.isLoaded()','WIND_PRESSURE_LIFT_FLOW.get()']:
 require(gate in material,'Resource/config gate '+gate)
require('if(!WindPressureLiftFlowMaterial.enabled()' in renderer and 'return false;' in renderer,'Unavailable enhancement uses original branch')
for state in ['LEQUAL_DEPTH_TEST','COLOR_WRITE','TRANSLUCENT_TRANSPARENCY','NO_CULL']:
 require(state in renderer,'Render footprint '+state)
require('ADDITIVE' not in renderer and 'POSITION_TEX_COLOR' in renderer,'No extra bloom or opaque pass')
report={'result':'PASS','unchanged_existing_classes':len(manifest)-len(changed),'reviewed_changed_classes':sorted(changed),'new_client_classes':sorted(actual.keys()-manifest.keys()),'same_movement_payment_protocol':True,'off_source_after_single_hook_removal_identical':True,'negative_controls':['changed OFF material','changed server class'],'limitations':['Source/class identity proof is not actual pixel equality','Existing historical three guards and restored whole-native-archive identity failure remain unchanged']}
out=ROOT/'build/reports/wind-pressure-flow-v72.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
