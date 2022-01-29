package com.hbm.tileentity.machine;

import java.util.List;

import com.hbm.interfaces.IFluidAcceptor;
import com.hbm.interfaces.IFluidContainer;
import com.hbm.interfaces.IFluidSource;
import com.hbm.inventory.FluidTank;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.RefineryRecipes;
import com.hbm.items.machine.ItemRTGPellet;
import com.hbm.items.machine.ItemRTGPelletDepleted;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.RTGUtil;
import com.hbm.util.Tuple.Quartet;

import api.hbm.energy.IEnergyGenerator;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import scala.actors.threadpool.Arrays;

public class TileEntityMachineRadiolysis extends TileEntityMachineBase implements IEnergyGenerator, IFluidAcceptor, IFluidSource, IFluidContainer {
	
	public long power;
	public int progress;
	public static final int maxProgress = 100;
	public static final int maxPower = 1000000;

	public FluidTank[] tanks;
	
	private static final int[] slot_io = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 13 };
	private static final int[] slot_rtg = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
	
	public TileEntityMachineRadiolysis() {
		super(14); //10 rtg slots, 2 fluid ID slots, 2 irradiation slots
		tanks = new FluidTank[3];
		tanks[0] = new FluidTank(Fluids.NONE, 8000, 0);
		tanks[1] = new FluidTank(Fluids.NONE, 8000, 1);
		tanks[2] = new FluidTank(Fluids.NONE, 8000, 2);
	}
	
	@Override
	public String getName() {
		return "container.radiolysis";
	}
	
	//IO Methods
	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemStack) {
		return i == 12 || (i < 10 && itemStack.getItem() instanceof ItemRTGPellet);
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return slot_io;
	}
	
	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return (i < 10 && itemStack.getItem() instanceof ItemRTGPelletDepleted) || i == 13;
	}
	
	//NBT Methods
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		
		this.power = nbt.getLong("power");
		this.progress = nbt.getInteger("progress");
		
		tanks[0].readFromNBT(nbt, "input");
		tanks[1].readFromNBT(nbt, "output1");
		tanks[2].readFromNBT(nbt, "output2");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		nbt.setLong("power", power);
		nbt.setInteger("progress", progress);
		
		tanks[0].writeToNBT(nbt, "input");
		tanks[1].writeToNBT(nbt, "output1");
		tanks[2].writeToNBT(nbt, "output2");
	}
	
	public void networkUnpack(NBTTagCompound data) {
		this.power = data.getLong("power");
		this.progress = data.getInteger("progress");
	}
	
	@Override
	public void updateEntity() {
		
		if(!worldObj.isRemote) {
			int heat = RTGUtil.updateRTGs(slots, slot_io);
			power += heat * 15;
			
			if(power > maxPower)
				power = maxPower;
			
			setupTanks();
			
			if(heat > 0) {
				progress += heat;
				if(progress >= maxProgress) {
					crack();
					progress = 0;
				}
			} else {
				progress = 0;
			}
			
			NBTTagCompound data = new NBTTagCompound();
			data.setLong("power", power);
			data.setInteger("progress", progress);
			this.networkPack(data, 50);
		}
	}
	
	//Processing Methods
	private boolean canDoRadiolysis() {
		
		
		
		return false;
	}
	
	private void crack() {
		
		Quartet<FluidType, FluidType, Integer, Integer> quart = RefineryRecipes.getCracking(tanks[0].getTankType());
		
		if(quart != null) {
			
			int left = quart.getY();
			int right = quart.getZ();
			
			if(tanks[0].getFill() >= 100 && hasSpace(left, right)) {
				tanks[0].setFill(tanks[0].getFill() - 100);
				tanks[1].setFill(tanks[2].getFill() + left);
				tanks[2].setFill(tanks[3].getFill() + right);
			}
		}
	}
	
	private boolean hasSpace(int left, int right) {
		return tanks[2].getFill() + left <= tanks[2].getMaxFill() && tanks[3].getFill() + right <= tanks[3].getMaxFill();
	}
	
	private void setupTanks() {
		
		Quartet<FluidType, FluidType, Integer, Integer> quart = RefineryRecipes.getCracking(tanks[0].getTankType());
		
		if(quart != null) {
			tanks[1].setTankType(quart.getW());
			tanks[2].setTankType(quart.getX());
		} else {
			tanks[0].setTankType(Fluids.NONE);
			tanks[1].setTankType(Fluids.NONE);
			tanks[2].setTankType(Fluids.NONE);
		}
		
	}
	
	//Power methods
	@Override
	public void setPower(long power) {
		this.power = power;
	}

	@Override
	public long getPower() {
		return power;
	}

	@Override
	public long getMaxPower() {
		return maxPower;
	}
	
	//Fluid Methods
	@Override
	public void setFillstate(int fill, int index) {
		if(index < 3 && tanks[index] != null)
			tanks[index].setFill(fill);
	}

	@Override
	public void setFluidFill(int fill, FluidType type) {
		for(FluidTank tank : tanks) {
			if(tank.getTankType() == type) {
				tank.setFill(fill);
			}
		}
	}

	@Override
	public void setType(FluidType type, int index) {
		this.tanks[index].setTankType(type);
	}

	@Override
	public List<FluidTank> getTanks() {
		return Arrays.asList(this.tanks);
	}

	@Override
	public int getFluidFill(FluidType type) {
		for(FluidTank tank : tanks) {
			if(tank.getTankType() == type) {
				return tank.getFill();
			}
		}
		return 0;
	}

	@Override
	public int getMaxFluidFill(FluidType type) {
		if(type == tanks[0].getTankType())
			return tanks[0].getMaxFill();
		else
			return 0;
	}

	@Override
	public void fillFluidInit(FluidType type) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void fillFluid(int x, int y, int z, boolean newTact, FluidType type) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean getTact() {
		return worldObj.getTotalWorldTime() % 10 == 0;
	}

	@Override
	public List<IFluidAcceptor> getFluidList(FluidType type) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void clearFluidList(FluidType type) {
		// TODO Auto-generated method stub
		
	}
}
