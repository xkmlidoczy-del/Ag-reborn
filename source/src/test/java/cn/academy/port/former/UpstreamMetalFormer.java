package cn.academy.port.former;
/** Executable unchanged classic method bodies; reference hash and exact extraction are checked. */
final class UpstreamMetalFormer extends UpstreamReceiver {
 enum Mode { PLATE, INCISE, ETCH, REFINE }
 static final int SLOT_IN=0,SLOT_OUT=1,SLOT_BATTERY=2,WORK_TICKS=60;
 static final double CONSUME_PER_TICK=13.3;
 Mode mode=Mode.PLATE;RecipeObject current;int workCounter,updateCounter,completed;
 ItemStack[] inventory=new ItemStack[3];
 ItemStack getStackInSlot(int slot){return inventory[slot];}
 void setInventorySlotContents(int slot,ItemStack stack){inventory[slot]=stack;}
 World getWorldObj(){return new World();}void sync(){}void updateSounds(){}
 static final class World {boolean isRemote;}
 static final class ItemStack {
  final String item;final int damage,max;int stackSize;double battery;
  ItemStack(String item,int count,int damage,int max){this.item=item;stackSize=count;this.damage=damage;this.max=max;}
  String getItem(){return item;}int getItemDamage(){return damage;}int getMaxStackSize(){return max;}
  ItemStack copy(){var out=new ItemStack(item,stackSize,damage,max);out.battery=battery;return out;}
 }
 static final class RecipeObject {
  final ItemStack input,output;final Mode mode;
  RecipeObject(ItemStack in,ItemStack out,Mode mode){input=in;output=out;this.mode=mode;}
  public boolean accepts(ItemStack stack, Mode mode2) {
            return  stack != null &&
                    mode == mode2 &&
                    input.getItem() == stack.getItem() &&
                    input.stackSize <= stack.stackSize && 
                    input.getItemDamage() == stack.getItemDamage();
        }
 }
 static final class MetalFormerRecipes {
  static final MetalFormerRecipes INSTANCE=new MetalFormerRecipes();
  java.util.List<RecipeObject> objects=new java.util.ArrayList<>();
  public RecipeObject getRecipe(ItemStack input, Mode mode) {
        for(RecipeObject recipe : objects) {
            if(recipe.accepts(input, mode))
                return recipe;
        }
        return null;
    }
 }
 static final class EnergyItemHelper {
  static boolean isSupported(ItemStack stack){return stack!=null&&stack.item=="battery";}
  static double pull(ItemStack stack,double request,boolean ignore){double taken=Math.min(Math.min(request,stack.battery),ignore?Double.MAX_VALUE:20);stack.battery-=taken;return taken;}
 }
public void updateEntity() {
        super.updateEntity();
        
        World world = getWorldObj();
        if(!world.isRemote) {
            if(current != null) {
                // Process recipe
                if(this.pullEnergy(CONSUME_PER_TICK) == CONSUME_PER_TICK && !isActionBlocked()) {
                    ++workCounter;
                    if(workCounter == WORK_TICKS) { // Finish the job.
                        ItemStack inputSlot = this.getStackInSlot(SLOT_IN);
                        ItemStack outputSlot = this.getStackInSlot(SLOT_OUT);
                        inputSlot.stackSize -= current.input.stackSize;
                        if(inputSlot.stackSize == 0)
                            this.setInventorySlotContents(SLOT_IN, null);
                        
                        if(outputSlot != null)
                            outputSlot.stackSize += current.output.stackSize;
                        else
                            this.setInventorySlotContents(SLOT_OUT, current.output.copy());
                        
                        current = null;
                        workCounter = 0;
                    }
                } else {
                    current = null;
                    workCounter = 0;
                }
            } else {
                if(++workCounter == 5) {
                    current = MetalFormerRecipes.INSTANCE.getRecipe(this.getStackInSlot(SLOT_IN), mode);
                    workCounter = 0;
                }
            }
            
            /* Process energy in/out */ {
                ItemStack stack = this.getStackInSlot(SLOT_BATTERY);
                if(stack != null && EnergyItemHelper.isSupported(stack)) {
                    double gain = EnergyItemHelper
                            .pull(stack, Math.min(getMaxEnergy() - getEnergy(), getBandwidth()), false);
                    this.injectEnergy(gain);
                }
            }
            
            if(++updateCounter == 10) {
                updateCounter = 0;
                sync();
            }
        } else {
            updateSounds();
        }
    }
public void cycleMode(int delta) {
        int nextOrd = mode.ordinal() + delta;
        if (nextOrd >= Mode.values().length) nextOrd = 0;
        else if (nextOrd < 0) nextOrd = Mode.values().length - 1;

        mode = Mode.values()[nextOrd];
        sync();
    }
private boolean isActionBlocked() {
        if(current == null) {
            return true;
        }
        
        ItemStack inputSlot = this.getStackInSlot(SLOT_IN), outputSlot = this.getStackInSlot(SLOT_OUT);
        return !(current.accepts(inputSlot, mode) && 
            (outputSlot == null || 
            (outputSlot.getItem() == current.output.getItem() && 
            outputSlot.getItemDamage() == current.output.getItemDamage() &&
            outputSlot.stackSize + current.output.stackSize <= outputSlot.getMaxStackSize())));
    }
public boolean isWorkInProgress() {
        return current != null;
    }
public double getWorkProgress() {
        return isWorkInProgress() ? (double) workCounter / WORK_TICKS : 0;
    }
}
class UpstreamReceiver {double energy;void updateEntity(){}double getMaxEnergy(){return 3000;}double getEnergy(){return energy;}double getBandwidth(){return 50;}double pullEnergy(double request){double taken=Math.min(request,energy);energy-=taken;return taken;}double injectEnergy(double request){double taken=Math.min(request,3000-energy);energy+=taken;return request-taken;}}
