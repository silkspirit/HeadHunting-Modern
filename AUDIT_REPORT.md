# HeadHunting Plugin Audit Report
**Date:** 2026-02-25
**Auditor:** Claude (subagent)

## Summary

Completed comprehensive audit of HeadHunting plugin including:
- Fixed all 20 broken mask textures
- Fixed Skeleton Horse Speed bug (levels 4-5 losing permanent speed)
- Reviewed ability progression for all masks
- Reviewed Java source code for bugs and issues

---

## 1. Texture Fixes ✅

**Problem:** All 20 mask textures were either truncated (hash < 64 chars) or returning HTTP 404 errors.

**Solution:** Replaced all textures with valid, working textures from minecraft-heads.com.

### Fixed Textures:

| Mask | Status | Notes |
|------|--------|-------|
| pig | ✅ Fixed | Was truncated (61 chars) |
| sheep | ✅ Fixed | Was truncated (60 chars) |
| chicken | ✅ Fixed | Was returning 404 |
| wolf | ✅ Fixed | Was truncated (61 chars) |
| rabbit | ✅ Fixed | Was returning 404 |
| horse | ✅ Fixed | Was truncated (62 chars) |
| cow | ✅ Fixed | Was truncated (62 chars) |
| spider | ✅ Fixed | Was returning 404 |
| skeleton | ✅ Fixed | Was returning 404 |
| enderman | ✅ Fixed | Was returning 404 |
| guardian | ✅ Fixed | Was truncated (63 chars) |
| blaze | ✅ Fixed | Was truncated (63 chars) |
| villager | ✅ Fixed | Was truncated (61 chars) |
| wither_skeleton | ✅ Fixed | Was truncated (62 chars) |
| iron_golem | ✅ Fixed | Was truncated (62 chars) |
| skeleton_horse | ✅ Fixed | Was truncated (60 chars) |
| undead_horse | ✅ Fixed | Was truncated (63 chars) |
| wither | ✅ Fixed | Was returning 404 |
| ender_dragon | ✅ Fixed | Was truncated (60 chars) |
| elder_guardian | ✅ Fixed | Was truncated (60 chars) |

**Verification:** All 20 texture URLs now return HTTP 200.

---

## 2. Skeleton Horse Speed Bug ✅

**Problem:** At levels 4-5, the Skeleton Horse mask was configured to replace permanent Speed with conditional Speed (only when low health). Players upgrading to level 4 would LOSE their permanent Speed III.

**Before (Broken):**
```yaml
3:
  - description: "Speed III"
    type: PASSIVE
    effect: POTION
    potion-type: SPEED
    amplifier: 2
    duration: -1
4:
  - description: "Speed IV, Absorption when low"
    type: PASSIVE
    effect: SPECIAL
    special-id: SKELETON_HORSE_PASSIVE  # ← No permanent speed!
```

**After (Fixed):**
```yaml
4:
  - description: "Speed III"
    type: PASSIVE
    effect: POTION
    potion-type: SPEED
    amplifier: 2
    duration: -1
  - description: "Speed IV + Absorption when low HP"
    type: PASSIVE
    effect: SPECIAL
    special-id: SKELETON_HORSE_PASSIVE
5:
  - description: "Speed IV"
    type: PASSIVE
    effect: POTION
    potion-type: SPEED
    amplifier: 3
    duration: -1
  - description: "Speed V + Absorption when low HP"
    type: PASSIVE
    effect: SPECIAL
    special-id: SKELETON_HORSE_PASSIVE_MAX
```

**Result:** Level 4 now keeps Speed III + gets low-HP bonus. Level 5 upgrades to Speed IV + improved low-HP bonus.

---

## 3. Ability Progression Review

Audited all 20 masks for similar progression bugs. No other critical issues found.

### Design Patterns Observed:

1. **Progressive Enhancement** (Good): Horse, Rabbit, Chicken - each level strictly improves
2. **Stacking Abilities** (Good): Most masks add new abilities at higher levels while keeping previous ones
3. **Replacement Pattern** (Acceptable): Some masks replace weaker versions with stronger ones (e.g., Pig potion duration 10% → 20% → 30%)

### Minor Notes:

- **Undead Horse:** Level progression is somewhat disjointed (Speed II → Slowness effect → Speed III → Slowness effect → Strip Regen). This is intentional design but could confuse players.
- **Cow Mask:** Levels 1-3 all have DEBUFF_CLEANSE with increasing chance. The code uses `getAllAbilitiesUpToLevel()` which will accumulate all three, but only the first proc wins. Consider consolidating.

---

## 4. Code Review Findings

### Overall Quality: Good ✅

The codebase is well-structured with proper separation of concerns:
- Managers for business logic
- Listeners for event handling
- Data classes for configuration
- Factories for item creation

### Minor Issues Found:

#### 1. MaskAbilityListener.java - Line 54
```java
// Debug: log the proc check
boolean procced = ability.shouldProc();

if (!procced) continue;
```
The debug comment is orphaned (no actual logging). Either add logging or remove comment.

#### 2. ConsumableListener.java - Line 40
```java
player.sendMessage(plugin.getConfigManager().getMessage("gapple-returned")
    .replace("Message not found: gapple-returned", "§a§lYour golden apple was returned!"));
```
This `.replace()` pattern is unusual. Better to check if message exists in ConfigManager or use a default message properly.

#### 3. MobDeathListener.java - Line 63-75
```java
// Check for stacked mobs
int stackSize = getStackSize(entity);
```
The `getStackSize()` method parses custom names with regex to find stack size. This could fail silently if mob stackers use different formats. Consider adding plugin API hooks for WildStacker, RoseStacker, etc.

#### 4. PearlListener.java - Double Event
Pearl landing triggers both `onPearlLand()` and `onTeleport()`, both of which call `abilityHandler.onPearlLand()`. This could result in double-applying invisibility in some edge cases. Consider tracking pearl UUIDs to prevent double-proc.

### Recommendations:

1. **Add null checks** in a few places where plugin integrations could fail
2. **Consolidate debug logging** - some files have commented debug, others have active debug
3. **Consider adding** a `MaskTier.isDivine()` helper instead of checking `tier == MaskTier.DIVINE` everywhere
4. **FarmingListener** uses deprecated `Material.CROPS` - update for newer MC versions

---

## 5. Files Modified

1. `/root/clawd/silk-server-reference/plugins/HeadHunting/masks.yml` - All textures + Skeleton Horse fix
2. `/root/clawd/HeadHunting/src/main/resources/masks.yml` - Synced with config

---

## 6. Files Reviewed (No Changes Needed)

- `HeadHunting.java` - Main plugin class ✅
- `MaskAbilityListener.java` - Combat abilities ✅
- `AbilityHandler.java` - Ability logic ✅
- `MaskManager.java` - Mask equip/abilities ✅
- `MaskConfig.java` - Config parsing ✅
- `MaskAbility.java` - Ability data class ✅
- `MaskFactory.java` - Item creation ✅
- `ConfigManager.java` - Config loading ✅
- `PlayerData.java` - Player data storage ✅
- `HealthListener.java` - Low-HP abilities ✅
- `DebuffListener.java` - Debuff immunity ✅
- `PearlListener.java` - Pearl abilities ✅
- `ConsumableListener.java` - Potion/gapple abilities ✅
- `FarmingListener.java` - Crop abilities ✅
- `MobDeathListener.java` - Head drops ✅

---

## Conclusion

The HeadHunting plugin is well-designed and functional. The main issues were:
1. **Data issues** (broken textures) - now fixed
2. **Config bug** (Skeleton Horse progression) - now fixed

The codebase is clean with only minor suggestions for improvement. No critical bugs or security issues found in the Java code.

---

## 7. Additional Fixes (Extended Audit)

### heads.yml Texture Fix ✅

**Problem:** The `heads.yml` file also had the same broken textures as `masks.yml`.

**Solution:** Updated all 20 head textures in `heads.yml` to match the fixed textures.

**Files Updated:**
- `/root/clawd/silk-server-reference/plugins/HeadHunting/heads.yml`
- `/root/clawd/HeadHunting/src/main/resources/heads.yml`

---

## 8. Integration Audit

### ShopGUIPlus Integration ✅
- Located at `/plugins/ShopGUIPlus/shops/heads.yml`
- References HeadHunting heads via `HeadHunting_Head:MOB_TYPE` pattern
- Sell prices are consistent with `heads.yml`
- No issues found

### Factions Integration ✅
- HeadHunting config properly references FactionsKore
- `warzone-drop-multiplier: 1.5` configured
- Darkzone detection configured in missions.yml

### WarzoneFishing Integration ⚠️
- **Note:** There are TWO fishing systems:
  1. `HeadHunting/fishing.yml` - Full fishing reward system
  2. `WarzoneFishing/config.yml` - Separate plugin
- These may overlap. Recommend reviewing if both are needed.

### PhoenixCrates Integration ✅
- Fishing rewards reference crate commands properly
- Pattern: `crate give {player} <type> 1`

### PlaceholderAPI ✅
- HeadHuntingExpansion.java provides placeholders
- No separate expansion JAR needed (built-in)

### LuckPerms ✅
- Permission system set to LUCKPERMS in config
- Level permissions granted automatically

---

## 9. Configuration Consistency Check

| Config File | Status | Notes |
|-------------|--------|-------|
| config.yml | ✅ Good | All settings reasonable |
| heads.yml | ✅ Fixed | Textures updated |
| masks.yml | ✅ Fixed | Textures + Skeleton Horse |
| levels.yml | ✅ Good | Progression balanced |
| missions.yml | ✅ Good | Requirements achievable |
| fishing.yml | ✅ Good | Rewards balanced |

---

## 10. Files Modified (Complete List)

1. `silk-server-reference/plugins/HeadHunting/masks.yml`
   - All 20 textures fixed
   - Skeleton Horse ability progression fixed
   
2. `silk-server-reference/plugins/HeadHunting/heads.yml`
   - All 20 textures fixed
   
3. `HeadHunting/src/main/resources/masks.yml`
   - Synced with live config
   
4. `HeadHunting/src/main/resources/heads.yml`
   - Synced with live config

---

## Recommendations for Future

1. **Remove WarzoneFishing plugin?** HeadHunting has its own fishing system that appears more feature-complete. Having both may confuse players.

2. **Add PlaceholderAPI expansion?** Currently built-in, but a standalone expansion JAR would allow updates without plugin rebuild.

3. **Consider adding** mob stacker API hooks (WildStacker, RoseStacker) for better stack detection.

4. **Update deprecated Materials** in FarmingListener for newer MC versions (Material.CROPS → Material.WHEAT).

---

**Status: Extended Audit Complete ✅**
