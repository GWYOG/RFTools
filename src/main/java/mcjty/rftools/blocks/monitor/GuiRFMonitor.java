package mcjty.rftools.blocks.monitor;

import mcjty.lib.base.StyleConfig;
import mcjty.lib.container.GenericGuiContainer;
import mcjty.lib.gui.Window;
import mcjty.lib.gui.events.ChoiceEvent;
import mcjty.lib.gui.events.DefaultSelectionEvent;
import mcjty.lib.gui.events.ValueEvent;
import mcjty.lib.gui.layout.HorizontalAlignment;
import mcjty.lib.gui.layout.HorizontalLayout;
import mcjty.lib.gui.layout.VerticalLayout;
import mcjty.lib.gui.widgets.*;
import mcjty.lib.gui.widgets.Label;
import mcjty.lib.gui.widgets.Panel;
import mcjty.lib.varia.Coordinate;
import mcjty.rftools.BlockInfo;
import mcjty.rftools.RFTools;
import mcjty.rftools.network.RFToolsMessages;
import net.minecraft.block.Block;
import net.minecraft.inventory.Container;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GuiRFMonitor extends GenericGuiContainer<RFMonitorBlockTileEntity> {
    private WidgetList list;
    private ChoiceLabel alarmModeChoiceLabel;
    private ScrollableLabel alarmLabel;
    private int listDirty;

    public static final int TEXT_COLOR_SELECTED = 0xFFFFFF;

    // A copy of the adjacent blocks we're currently showing
    private List<Coordinate> adjacentBlocks = null;

    // From server.
    public static List<Coordinate> fromServer_clientAdjacentBlocks = null;


    public GuiRFMonitor(RFMonitorBlockTileEntity monitorBlockTileEntity, Container container) {
        super(RFTools.instance, RFToolsMessages.INSTANCE, monitorBlockTileEntity, container, RFTools.GUI_MANUAL_MAIN, "monitor");

        xSize = 256;
        ySize = 180;
    }

    @Override
    public void initGui() {
        super.initGui();

        list = new WidgetList(mc, this).addSelectionEvent(new DefaultSelectionEvent() {
            @Override
            public void select(Widget parent, int index) {
                setSelectedBlock(index);
            }
        });
        listDirty = 0;
        Slider listSlider = new Slider(mc, this).setDesiredWidth(10).setVertical().setScrollable(list);
        Panel listPanel = new Panel(mc, this).setLayout(new HorizontalLayout().setHorizontalMargin(3).setSpacing(1)).addChild(list).addChild(listSlider);

        alarmModeChoiceLabel = new ChoiceLabel(mc, this).addChoices(
                RFMonitorMode.MODE_OFF.getDescription(), RFMonitorMode.MODE_LESS.getDescription(), RFMonitorMode.MODE_MORE.getDescription()).
                setDesiredWidth(60).setDesiredHeight(15).
                setTooltips("Control when a redstone", "signal should be sent").
                addChoiceEvent(new ChoiceEvent() {
                    @Override
                    public void choiceChanged(Widget parent, String newChoice) {
                        changeAlarmMode(RFMonitorMode.getModeFromDescription(newChoice));
                    }
                });
        alarmModeChoiceLabel.setChoice(tileEntity.getAlarmMode().getDescription());

        alarmLabel = new ScrollableLabel(mc, this).setSuffix("%").setDesiredWidth(30).setRealMinimum(0).setRealMaximum(100).
                setRealValue(tileEntity.getAlarmLevel()).
                addValueEvent(new ValueEvent() {
                    @Override
                    public void valueChanged(Widget parent, int newValue) {
                        changeAlarmValue(newValue);
                    }
                });
        Slider alarmSlider = new Slider(mc, this).
                setDesiredHeight(15).
                setHorizontal().
                setMinimumKnobSize(15).
                setTooltips("Alarm level").
                setScrollable(alarmLabel);
        Panel alarmPanel = new Panel(mc, this).setLayout(new HorizontalLayout()).addChild(alarmModeChoiceLabel).addChild(alarmSlider).addChild(alarmLabel).setDesiredHeight(20);

        Widget toplevel = new Panel(mc, this).setFilledRectThickness(2).setLayout(new VerticalLayout()).addChild(listPanel).addChild(alarmPanel);
        toplevel.setBounds(new Rectangle(guiLeft, guiTop, xSize, ySize));
        window = new Window(this, toplevel);

        fromServer_clientAdjacentBlocks = new ArrayList<Coordinate>();
        RFToolsMessages.INSTANCE.sendToServer(new PacketGetAdjacentBlocks(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord));
    }

    private void changeAlarmMode(RFMonitorMode mode) {
        int alarmLevel = alarmLabel.getRealValue();
        tileEntity.setAlarm(mode, alarmLevel);
        RFToolsMessages.INSTANCE.sendToServer(new PacketContentsMonitor(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, mode, alarmLevel));
    }

    private void changeAlarmValue(int newValue) {
        RFMonitorMode mode = RFMonitorMode.getModeFromDescription(alarmModeChoiceLabel.getCurrentChoice());
        tileEntity.setAlarm(mode, newValue);
        RFToolsMessages.INSTANCE.sendToServer(new PacketContentsMonitor(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, mode, newValue));
    }

    private void refreshList() {
    }

    private void setSelectedBlock(int index) {
        if (index != -1) {
            Coordinate c = adjacentBlocks.get(index);
            tileEntity.setMonitor(c);
            RFToolsMessages.INSTANCE.sendToServer(new PacketContentsMonitor(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, c));
        } else {
            tileEntity.setInvalid();
            RFToolsMessages.INSTANCE.sendToServer(new PacketContentsMonitor(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, Coordinate.INVALID));
        }
    }

    private void populateList() {
        List<Coordinate> newAdjacentBlocks = fromServer_clientAdjacentBlocks;
        if (newAdjacentBlocks == null) {
            return;
        }
        if (newAdjacentBlocks.equals(adjacentBlocks)) {
            refreshList();
            return;
        }

        adjacentBlocks = new ArrayList<Coordinate>(newAdjacentBlocks);
        list.removeChildren();

        int index = 0, sel = -1;
        for (Coordinate coordinate : adjacentBlocks) {
            Block block = mc.theWorld.getBlock(coordinate.getX(), coordinate.getY(), coordinate.getZ());
            int meta = mc.theWorld.getBlockMetadata(coordinate.getX(), coordinate.getY(), coordinate.getZ());

            int color = StyleConfig.colorTextInListNormal;

            String displayName = BlockInfo.getReadableName(block, coordinate, meta, mc.theWorld);

            if (coordinate.getX() == tileEntity.getMonitorX() &&
                    coordinate.getY() == tileEntity.getMonitorY() &&
                    coordinate.getZ() == tileEntity.getMonitorZ()) {
                sel = index;
                color = TEXT_COLOR_SELECTED;
            }

            Panel panel = new Panel(mc, this).setLayout(new HorizontalLayout());
            panel.addChild(new BlockRender(mc, this).setRenderItem(block));
            panel.addChild(new Label(mc, this).setText(displayName).setColor(color).setHorizontalAlignment(HorizontalAlignment.ALIGH_LEFT).setDesiredWidth(90));
            panel.addChild(new Label(mc, this).setDynamic(true).setText(coordinate.toString()).setColor(color));
            list.addChild(panel);

            index++;
        }

        list.setSelected(sel);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float v, int i, int i2) {
        listDirty--;
        if (listDirty <= 0) {
            populateList();
            listDirty = 5;
        }

        drawWindow();
    }
}
