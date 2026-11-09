#!/usr/bin/env python3
"""Unit-level rejection tests for evidence selection; never accepts a game run."""
import importlib.util,json,tempfile
from pathlib import Path
S=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('a',S/'qa/check-canon-complete-matrix.py');m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
def main():
 with tempfile.TemporaryDirectory(prefix='aca-provenance-unit-') as temp:
  t=Path(temp);run=t/'run';run.mkdir();name=next(iter(sorted(m.MOTION_OWNER_CLASSES)));old='1'*64;new='2'*64;shared='classes/java/main/cn/academy/port/client/CanonVfxRenderer.class'
  actual={name:old,shared:'3'*64};reference={name:new,shared:'3'*64};(run/'compiled-snapshot.json').write_text(json.dumps(actual))
  audit=t/'unit-audit.json';audit.write_text(json.dumps({**{k:True for k in ['pass','staticInitializerUnchanged','dispatchUnchanged','unrelatedCategoryBranchesUnchanged','sharedRenderNetworkCoreResourcesUnchanged']},'approvedChangedClasses':[name],'classHashTransitions':{name:{'before':old,'after':new}}}))
  policy={'schemaVersion':1,'policyId':'canon-motion-owner-20261030-v1','approved':True,'forceRetestCategories':sorted(m.MOTION_RETEST),'reuseCategories':sorted(m.MOTION_REUSE),'classHashTransitions':{name:{'before':old,'after':new}},'independentAudit':{'path':str(audit),'sha256':m.sha(audit)}};file=t/'unit-policy.json';file.write_text(json.dumps(policy));p=m.provenance_policy(file,reference)
  checks={}
  checks['default_strict_rejects_owner_drift']=not m.provenance(run,reference,'paper_psychokinesis')['currentOwningProductionCompatible']
  checks['explicit_policy_reuses_only_unrelated_branch']=m.provenance(run,reference,'paper_psychokinesis',p)['currentOwningProductionCompatible']
  checks['exact_snapshot_flag_stays_false_on_policy_reuse']=not m.provenance(run,reference,'paper_psychokinesis',p)['exactProductionSnapshotMatches']
  checks['all_four_affected_families_rejected']=all(not m.provenance(run,reference,c,p)['currentOwningProductionCompatible'] for c in m.MOTION_RETEST)
  changed=dict(reference);changed[shared]='4'*64;checks['any_shared_renderer_change_rejected']=not m.provenance(run,changed,'paper_psychokinesis',p)['currentOwningProductionCompatible']
  changed=dict(reference);changed[name]='5'*64;checks['unknown_new_class_hash_rejected']=not m.provenance(run,changed,'paper_psychokinesis',p)['currentOwningProductionCompatible']
  actual[name]='6'*64;(run/'compiled-snapshot.json').write_text(json.dumps(actual));checks['unknown_old_class_hash_rejected']=not m.provenance(run,reference,'paper_psychokinesis',p)['currentOwningProductionCompatible']
  policy['approved']=False;file.write_text(json.dumps(policy))
  try:m.provenance_policy(file,reference);checks['unapproved_policy_rejected']=False
  except ValueError:checks['unapproved_policy_rejected']=True
  audit.write_text('{}')
  policy['approved']=True;file.write_text(json.dumps(policy))
  try:m.provenance_policy(file,reference);checks['audit_tamper_rejected']=False
  except ValueError:checks['audit_tamper_rejected']=True
  # A second explicit policy can only cover the exact Dark-only render branch.
  # It cannot accidentally exempt the four server owners of the motion repair.
  audit.write_text(json.dumps({**{k:True for k in ['pass','staticInitializerUnchanged','dispatchUnchanged','unrelatedCategoryBranchesUnchanged','sharedRenderNetworkCoreResourcesUnchanged']},'approvedChangedClasses':[name],'classHashTransitions':{name:{'before':old,'after':new}}}));policy['independentAudit']['sha256']=m.sha(audit);file.write_text(json.dumps(policy))
  actual={name:old,shared:'3'*64,m.DARK_RENDER_CLASS:'7'*64};reference={name:new,shared:'3'*64,m.DARK_RENDER_CLASS:'8'*64};(run/'compiled-snapshot.json').write_text(json.dumps(actual))
  evidence=t/'render-support.json';evidence.write_text('{"unitOnly":true}')
  renderaudit=t/'unit-render-audit.json';required=['pass','onlyDarkTargetSegmentChanged','nonDarkCategoryBranchesUnchanged','otherMethodsBytecodeUnchanged','serverNetworkCoreResourcesUnchanged','fullDark24Retested','eightFamilyRepresentativeRetested'];transition={m.DARK_RENDER_CLASS:{'before':'7'*64,'after':'8'*64}}
  renderaudit.write_text(json.dumps({**{k:True for k in required},'classHashTransitions':transition,'evidence':[{'path':str(evidence),'sha256':m.sha(evidence)}]}))
  rp={'schemaVersion':1,'policyId':'canon-dark-target-render-20261030-v1','approved':True,'classHashTransitions':transition,'forceRetestCategories':['dark_matter'],'reuseCategories':sorted((m.MOTION_RETEST|m.MOTION_REUSE)-{'dark_matter'}),'independentAudit':{'path':str(renderaudit),'sha256':m.sha(renderaudit)}};rf=t/'unit-render-policy.json';rf.write_text(json.dumps(rp));p=m.attach_render_policy(m.provenance_policy(file,reference),rf,reference)
  checks['combined_policies_allow_only_exact_unrelated_deltas']=m.provenance(run,reference,'hydro_hand',p)['currentOwningProductionCompatible']
  checks['combined_policies_still_reject_all_four_motion_owners']=all(not m.provenance(run,reference,c,p)['currentOwningProductionCompatible'] for c in m.MOTION_RETEST)
  actual[name]=new;(run/'compiled-snapshot.json').write_text(json.dumps(actual))
  checks['render_only_delta_can_reuse_new_motion_owner_receipt']=m.provenance(run,reference,'nitrogen_armor',p)['currentOwningProductionCompatible']
  checks['render_only_delta_never_reuses_dark']=not m.provenance(run,reference,'dark_matter',p)['currentOwningProductionCompatible']
  changed=dict(reference);changed[shared]='9'*64;checks['combined_policy_rejects_additional_shared_drift']=not m.provenance(run,changed,'hydro_hand',p)['currentOwningProductionCompatible']
  actual[m.DARK_RENDER_CLASS]='9'*64;(run/'compiled-snapshot.json').write_text(json.dumps(actual));checks['unknown_render_before_hash_rejected']=not m.provenance(run,reference,'hydro_hand',p)['currentOwningProductionCompatible']
  rp['approved']=False;rf.write_text(json.dumps(rp))
  try:m.attach_render_policy(m.provenance_policy(file,reference),rf,reference);checks['unapproved_render_policy_rejected']=False
  except ValueError:checks['unapproved_render_policy_rejected']=True
  rp['approved']=True;rf.write_text(json.dumps(rp));evidence.write_text('{}')
  try:m.attach_render_policy(m.provenance_policy(file,reference),rf,reference);checks['render_support_tamper_rejected']=False
  except ValueError:checks['render_support_tamper_rejected']=True
  # A reviewed final patch may chain the historical hashes, but cannot
  # exempt either newly affected category or invent another transition.
  evidence.write_text('{"unitOnly":true}');rp['independentAudit']['sha256']=m.sha(renderaudit);rf.write_text(json.dumps(rp))
  owner='classes/java/main/cn/academy/port/canon/CanonExpansionCombatA.class'
  historical={name:new,shared:'3'*64,m.DARK_RENDER_CLASS:'8'*64,m.DARK_RENDER_SWITCH_CLASS:'a'*64,m.FINAL_PENDING_CLASS:'a'*64,owner:'b'*64}
  finalref={**historical,owner:'c'*64,m.DARK_RENDER_CLASS:'d'*64,m.DARK_RENDER_SWITCH_CLASS:'e'*64,m.FINAL_PENDING_CLASS:'f'*64}
  transitions={k:{'before':historical[k],'after':finalref[k]} for k in m.FINAL_PATCH_CLASSES}
  finalaudit=t/'unit-final-audit.json';required=['pass','existingOwnerMethodsUnchanged','onlyClearedMentalTargetEventAdded','mentalRendererBranchUnchanged','onlyFloatTargetBindingChanged','syntheticSwitchMapBytecodeUnchanged','pendingRecordBytecodeUnchanged','allOtherProductionBytesUnchanged','native262Passed','freshMental24Passed','freshFloat20Passed','freshFourFloatAlliesPassed']
  finalaudit.write_text(json.dumps({**{k:True for k in required},'classHashTransitions':transitions,'evidence':[{'path':str(evidence),'sha256':m.sha(evidence)}]}))
  finalpolicy={'schemaVersion':1,'policyId':'canon-cleared-target-float-final-20261030-v1','approved':True,'forceRetestCategories':['mental_out','float_dial'],'classHashTransitions':transitions,'independentAudit':{'path':str(finalaudit),'sha256':m.sha(finalaudit)}}
  finalfile=t/'unit-final-policy.json';finalfile.write_text(json.dumps(finalpolicy));p=m.load_policies(file,rf,finalref,finalfile)
  (run/'compiled-snapshot.json').write_text(json.dumps(historical))
  checks['final_exact_three_classes_can_reuse_unrelated_owner']=m.provenance(run,finalref,'dark_matter',p)['currentOwningProductionCompatible']
  checks['final_two_affected_families_require_fresh']=all(not m.provenance(run,finalref,c,p)['currentOwningProductionCompatible'] for c in ['mental_out','float_dial'])
  checks['final_reuse_never_claims_exact_identity']=not m.provenance(run,finalref,'paper_psychokinesis',p)['exactProductionSnapshotMatches']
  chain={**historical,name:old,m.DARK_RENDER_CLASS:'7'*64};(run/'compiled-snapshot.json').write_text(json.dumps(chain))
  checks['final_can_chain_two_historical_exact_transitions']=m.provenance(run,finalref,'paper_psychokinesis',p)['currentOwningProductionCompatible']
  checks['final_chain_keeps_old_motion_owner_rejection']=not m.provenance(run,finalref,'dark_matter',p)['currentOwningProductionCompatible']
  (run/'compiled-snapshot.json').write_text(json.dumps({**historical,owner:'f'*64}))
  checks['final_unknown_before_hash_rejected']=not m.provenance(run,finalref,'paper_psychokinesis',p)['currentOwningProductionCompatible']
  (run/'compiled-snapshot.json').write_text(json.dumps({**historical,shared:'f'*64}))
  checks['final_additional_shared_delta_rejected']=not m.provenance(run,finalref,'paper_psychokinesis',p)['currentOwningProductionCompatible']
  (run/'compiled-snapshot.json').write_text(json.dumps(finalref))
  checks['final_actual_exact_snapshot_needs_no_reuse']=m.provenance(run,finalref,'mental_out',p)['exactProductionSnapshotMatches']
  intermediate='0'*64
  mixed={**finalref,m.DARK_RENDER_CLASS:intermediate,m.DARK_RENDER_SWITCH_CLASS:historical[m.DARK_RENDER_SWITCH_CLASS],m.FINAL_PENDING_CLASS:historical[m.FINAL_PENDING_CLASS]}
  (run/'compiled-snapshot.json').write_text(json.dumps(mixed))
  checks['final_unreviewed_intermediate_rejected']=not m.provenance(run,finalref,'mental_out',p)['currentOwningProductionCompatible']
  finalpolicy['intermediateClassHashes']={m.DARK_RENDER_CLASS:[intermediate]}
  review=json.loads(finalaudit.read_text());review.update(intermediateFloatOnlyRendererBranchesReviewed=True,intermediateClassHashes=finalpolicy['intermediateClassHashes']);finalaudit.write_text(json.dumps(review));finalpolicy['independentAudit']['sha256']=m.sha(finalaudit);finalfile.write_text(json.dumps(finalpolicy));p=m.load_policies(file,rf,finalref,finalfile)
  checks['final_reviewed_intermediate_preserves_current_mental_owner']=m.provenance(run,finalref,'mental_out',p)['currentOwningProductionCompatible']
  checks['final_reviewed_intermediate_never_exempts_float']=not m.provenance(run,finalref,'float_dial',p)['currentOwningProductionCompatible']
  finalpolicy['approved']=False;finalfile.write_text(json.dumps(finalpolicy))
  try:m.load_policies(file,rf,finalref,finalfile);checks['final_unapproved_policy_rejected']=False
  except ValueError:checks['final_unapproved_policy_rejected']=True
  print(json.dumps({'unitOnly':True,'doesNotApproveAnyActualNativeEvidence':True,'pass':all(checks.values()),'checks':checks},indent=2));assert all(checks.values())
if __name__=='__main__':main()
