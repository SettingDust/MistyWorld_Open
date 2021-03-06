package ru.liahim.mist.entity;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.collect.Sets;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import ru.liahim.mist.api.item.MistItems;
import ru.liahim.mist.api.loottable.LootTables;
import ru.liahim.mist.api.sound.MistSounds;
import ru.liahim.mist.common.MistTime;
import ru.liahim.mist.entity.ai.EntityAIFollowGender;
import ru.liahim.mist.entity.ai.EntityAIFollowParentGender;
import ru.liahim.mist.entity.ai.EntityAIMateGender;
import ru.liahim.mist.entity.ai.EntityAITemptTamed;

public class EntityCaravan extends EntityAlbino {

	private static final DataParameter<Integer> MILK_TIME = EntityDataManager.<Integer>createKey(EntityCaravan.class, DataSerializers.VARINT);
	private static final Set<ItemStack> TEMPTATION_STACKS = Sets.newHashSet(new ItemStack(MistItems.MUSHROOMS_FOOD, 1, 12), new ItemStack(MistItems.MUSHROOMS_FOOD, 1, 27));
	private static long pregnantTime = MistTime.getDayInMonth() * 36000;
	private int milkTimer;

	public EntityCaravan(World world) {
		super(world);
		this.setSize(1.6F, 2.375F);
	}

	@Override
	protected void initEntityAI() {
		this.tasks.addTask(0, new EntityAISwimming(this));
		this.tasks.addTask(1, new EntityAIPanic(this, 1.5D));
		this.tasks.addTask(2, new EntityAIMateGender(this, 1.0D));
		this.tasks.addTask(3, new EntityAITemptTamed(this, 1.0D, 1.1D, true, TEMPTATION_STACKS));
		this.tasks.addTask(4, new EntityAIFollowParentGender(this, 1.25D));
		this.tasks.addTask(5, new EntityAIFollowGender(this, 1.0D));
		this.tasks.addTask(6, new EntityAIAvoidEntity(this, EntityPlayer.class, 16, 1.0D, 1.25D));
		this.tasks.addTask(7, new EntityAIWanderAvoidWater(this, 1.0D));
		this.tasks.addTask(8, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
		this.tasks.addTask(9, new EntityAILookIdle(this));
	}

	@Override
	public float getEyeHeight() {
		return this.isChild() ? this.height : 1.0F;
	}

	@Override
	protected void updateAITasks() {
		if (this.milkTimer > 0) {
			--this.milkTimer;
			if (this.milkTimer == 0) updateMilkTimer();
		}
		super.updateAITasks();
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(30.0D);
		this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.20000000298023224D);
	}

	@Override
	protected void entityInit() {
		super.entityInit();
		this.dataManager.register(MILK_TIME, 0);
	}

	@Override
	public void notifyDataManagerChange(DataParameter<?> key) {
		if (MILK_TIME.equals(key) && this.world.isRemote) {
			this.milkTimer = this.dataManager.get(MILK_TIME);
		}
		super.notifyDataManagerChange(key);
	}

	private void updateMilkTimer() {
		this.dataManager.set(MILK_TIME, this.milkTimer);
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound compound) {
		super.writeEntityToNBT(compound);
		compound.setInteger("MilkTime", this.milkTimer);
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound compound) {
		super.readEntityFromNBT(compound);
		this.milkTimer = compound.getInteger("MilkTime");
		updateMilkTimer();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return MistSounds.ENTITY_CARAVAN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
		return MistSounds.ENTITY_CARAVAN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MistSounds.ENTITY_CARAVAN_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, Block blockIn) {
		this.playSound(SoundEvents.ENTITY_COW_STEP, 0.15F, 1.0F);
	}

	@Override
	@Nullable
	protected ResourceLocation getLootTable() {
		return LootTables.CARAVAN_LOOT;
	}

	@Override
	protected int getSkillPoint() {
		return 2;
	}

	@Override
	protected void dropLoot(boolean wasRecentlyHit, int lootingModifier, DamageSource source) {
		if (this.rand.nextInt(temptationStackDropChance) == 0) {
			this.entityDropItem((ItemStack)TEMPTATION_STACKS.toArray()[this.rand.nextInt(TEMPTATION_STACKS.size())], 0);
		}
		super.dropLoot(wasRecentlyHit, lootingModifier, source);
	}

	@Override
	public boolean processInteract(EntityPlayer player, EnumHand hand) {
		if (this.revengeTimer > 0 && !player.capabilities.isCreativeMode) return true;
		ItemStack stack = player.getHeldItem(hand);
		if (stack.getItem() == Items.SPAWN_EGG) return super.processInteract(player, hand);
		if (this.isTamed() || player.capabilities.isCreativeMode) {
			if (this.milkTimer == 0 && this.isFemale() && stack.getItem() == Items.BUCKET && !player.capabilities.isCreativeMode && !this.isChild()) {
				player.playSound(SoundEvents.ENTITY_COW_MILK, 1.0F, 1.0F);
				stack.shrink(1);
				if (stack.isEmpty()) player.setHeldItem(hand, new ItemStack(Items.MILK_BUCKET));
				else if (!player.inventory.addItemStackToInventory(new ItemStack(Items.MILK_BUCKET))) {
					player.dropItem(new ItemStack(Items.MILK_BUCKET), false);
				}
				this.milkTimer = 12000;
				updateMilkTimer();
				return true;
			} else return super.processInteract(player, hand);
		}
		return tamedProcess(player, stack);
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float amount) {
		boolean flag = super.attackEntityFrom(source, amount);
		Entity entity = source.getTrueSource();
		if (flag && entity != null && entity instanceof EntityPlayer && !((EntityPlayer)entity).capabilities.isCreativeMode) {
			if (this.getHealth() <= 0.0F) this.<EntityCaravan>closePanic(EntityCaravan.class, (EntityPlayer)entity, this.rand.nextInt(4000) + 4000);
			else this.<EntityCaravan>closePanic(EntityCaravan.class, (EntityPlayer)entity, this.rand.nextInt(250) + 250);
			this.setRevengeTime(this.rand.nextInt((int)(500 * amount) + 1) + (int)(250 * amount) + 250);
		}
		return flag;
	}

	@Override
	protected EntityAlbino getChild() {
		return new EntityCaravan(this.world);
	}

	@Override
	public boolean childCheck() {
		List<EntityCaravan> list = this.world.<EntityCaravan>getEntitiesWithinAABB(this.getClass(), this.getEntityBoundingBox().grow(8.0D, 4.0D, 8.0D));
		for (EntityCaravan entity : list) {
			if (entity.isChild() && (!entity.canBeTempted() || !entity.isTamed())) return false;
		}
		return true;
	}

	@Override
	protected long getPregnantTime() {
		return EntityCaravan.pregnantTime;
	}

	@Override
	public int getChildCount() {
		return 1;
	}

	@Override
	public boolean initFemale() {
		return this.rand.nextInt(3) > 0;
	}

	@Override
	public boolean isBreedingItem(ItemStack stack) {
		for (ItemStack st : EntityCaravan.TEMPTATION_STACKS) {
			if (stack.getItem() == st.getItem() && stack.getItemDamage() == st.getItemDamage()) return true;
		}
		return false;
	}

	@Override
	public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, IEntityLivingData livingdata) {
		super.onInitialSpawn(difficulty, livingdata);
		if (livingdata instanceof EntityCaravan.GroupData) {
			if (((EntityCaravan.GroupData)livingdata).init) {
				this.setFemale(false);
				((EntityCaravan.GroupData)livingdata).init = false;
			} else this.setFemale(true);
		} else livingdata = new EntityCaravan.GroupData();
		return livingdata;
	}

	@Override
	public int getTameLevel() {
		return 2;
	}

	static class GroupData implements IEntityLivingData {
		public boolean init = true;
		private GroupData() {}
	}
}