// Copyright 2018 by George Mason University
// Licensed under the Apache 2.0 License


package flow.modules;

import flow.*;
import java.io.*;
import java.util.zip.*;
import java.util.*;
import java.awt.*;
import javax.swing.*;
import flow.gui.*;
import org.json.*;

/**  
     A special Unit which converts loaded patches into single modules to be used in higher-level
     macros.  Macro works in conjunction with the Units "In" and "Out" to route modulation
     and unit signals from the higher-level patch into the embedded lower-level patch and back
     out again.  Macro has four incoming modulations and four incoming units.  Signals
     connected to these incoming elements are routed out the equivalent elements in any "In"
     Unit inside the embedded patch.  Furthermore, Macro has four outgoing modulations and
     four outgoing units.  Signals attached to the equivalent elements in the last (rightmost)
     "Out" unit in the embedded patch will be routed out these to the higher level patch.
        
     <p>The "In" and "Out" units can have their unit and modulation connections named by the user.
     Macro respects these and names its equivalent elements in the same way.  Then name of the
     Macro, displayed in the title bar of its ModulePanel, will be the patch name.
*/
        
public class Macro extends Unit implements Cloneable
    {
    private static final long serialVersionUID = 1;

    public static final String PATCH_NAME_KEY = "Patch Name";

    public static final int MOD_ON_TR = In.NUM_MOD_INPUTS;
    public static final int MOD_OFF_TR = In.NUM_MOD_INPUTS + 1;
    public static final int MOD_PAUSE_TR = In.NUM_MOD_INPUTS + 2;
    
    public static final int OPTION_PAUSE = 0;
    
    public static final boolean INCLUDE_PAUSE = true;
    
    Modulation[] modules = new Modulation[0];
    Out out;
    ArrayList<In> ins = new ArrayList<>();
    String patchName = Sound.UNTITLED_PATCH_NAME;
    
    boolean pause = false;
    public boolean getPause() { return pause; }
    public void setPause(boolean val) { pause = val; } 
    
    boolean[] unitOuts = new boolean[Out.NUM_UNIT_OUTPUTS];
    boolean[] unitIns = new boolean[In.NUM_UNIT_INPUTS];
    boolean[] modOuts = new boolean[Out.NUM_MOD_OUTPUTS];
    boolean[] modIns = new boolean[In.NUM_MOD_INPUTS];


    public int getOptionValue(int option) 
        { 
        switch(option)
            {
            case OPTION_PAUSE: return getPause() ? 1 : 0;
            default: throw new RuntimeException("No such option " + option);
            }
        }
                
    public void setOptionValue(int option, int value)
        { 
        switch(option)
            {
            case OPTION_PAUSE: setPause(value != 0); return;
            default: throw new RuntimeException("No such option " + option);
            }
        }


    public Object clone()
        {
        Macro obj = (Macro)(super.clone());
        obj.unitOuts = (boolean[])(obj.unitOuts.clone());
        obj.unitIns = (boolean[])(obj.unitIns.clone());
        obj.modOuts = (boolean[])(obj.modOuts.clone());
        obj.modIns = (boolean[])(obj.modIns.clone());
        obj.modules = (Modulation[])(obj.modules.clone());
        for(int i = 0; i < obj.modules.length; i++)
            obj.modules[i] = (Modulation)(obj.modules[i].clone());
            
        // set up backpointer
        for(int i = 0; i < obj.modules.length; i++)
            obj.modules[i].setMacro(obj);
                
        // Here we need to rewire the modules again
        // We first build a map of old modules to new ones
        HashMap <Modulation, Modulation> map = new HashMap<>();
        for(int i = 0; i < modules.length; i++)
            {
            map.put(modules[i], obj.modules[i]);
            }
        
        // Now build the modulation input and unit inputs, pointing to the new modules
        for(int i = 0; i < modules.length; i++)
            {
            for(int j = 0; j < modules[i].getNumModulations(); j++)
                {
                if (modules[i].getModulation(j) instanceof Constant)
                    {
                    obj.modules[i].setModulation((Modulation)(modules[i].getModulation(j).clone()), j, modules[i].getModulationIndex(j));
                    }
                else
                    {
                    obj.modules[i].setModulation(map.get(modules[i].getModulation(j)), j, modules[i].getModulationIndex(j));
                    }
                }
            if (modules[i] instanceof Unit)
                {
                for(int j = 0; j < ((Unit)modules[i]).getNumInputs(); j++)
                    {
                    if (((Unit)(modules[i])).getInput(j) instanceof Nil)
                        {
                        ((Unit)(obj.modules[i])).setInput(Unit.NIL, j, ((Unit)modules[i]).getInputIndex(j));
                        }
                    else
                        {
                        ((Unit)(obj.modules[i])).setInput(((Unit)(map.get(((Unit)modules[i]).getInput(j)))), j, ((Unit)modules[i]).getInputIndex(j));
                        }
                    }
                }
            }
        
        // We also need to identify the new "Out" and the ins again.
        // We do this by simply calling loadModules
        obj.ins = new ArrayList<In>();
        obj.out = null;
        obj.loadModules(obj.modules, obj.patchName);
    
        return obj;
        }

    public String getNameForModulation() { return patchName; }
    
    public int getOutputTriggerCount(int num)
        {
        if (out != null && out.getNumModulationOutputs() > num)
            return out.getOutputTriggerCount(num);
        else return NO_TRIGGER;
        }
    
    public boolean isOutputTriggered(int num)
        {
        if (out != null && out.getNumModulationOutputs() > num)
            return out.isOutputTriggered(num);
        else return false;
        }
    
    public void setTriggerValues(boolean isTriggered, int triggerCount, int num)
        {
        if (out != null && out.getNumModulationOutputs() > num)
            {
            out.setTriggerValues(isTriggered, triggerCount, num);
            }
        }

    public void reset()
        {
        super.reset();
        for(int i = 0; i < modules.length; i++)
            modules[i].reset();
        }

    public void gate()
        {
        super.gate();
        if (isModulationConstant(MOD_ON_TR))
        	for(int i = 0; i < modules.length; i++)
        	    modules[i].gate();
        }
 
    public void release()
        {
        super.release();
		if (isModulationConstant(MOD_OFF_TR))
            for(int i = 0; i < modules.length; i++)
        	    modules[i].release();
        }
   
    public void go()
        {
        super.go();
        
        if (isTriggered(MOD_ON_TR))
        	for(int i = 0; i < modules.length; i++)
        	    modules[i].gate();

        if (isTriggered(MOD_OFF_TR))
            for(int i = 0; i < modules.length; i++)
        	    modules[i].release();
        	    
        if (getPause() && modulate(MOD_ON_TR) == 0.0)
        	{
        	return;		// pause
        	}

        double note = sound.getNote();

        for(int i = 0; i < modules.length; i++)
            modules[i].go();
                
        if (out != null)
            {
            int len = Out.NUM_MOD_OUTPUTS;  // skip gain etc.
            for(int i = 0; i < len; i++)
                {
                setModulationOutput(i, out.modulate(i));
                }
                        
            len = out.getNumInputs();
            for(int i = 0; i < len; i++)
                {
                double[] amplitudes = getAmplitudes(i);
                double[] frequencies = getFrequencies(i);
                byte[] orders = getOrders(i);
                        
                System.arraycopy(out.getFrequenciesIn(i), 0, frequencies, 0, frequencies.length);
                System.arraycopy(out.getOrdersIn(i), 0, orders, 0, orders.length);
                System.arraycopy(out.getAmplitudesIn(i), 0, amplitudes, 0, amplitudes.length);
                // we handle the gain here, since we are extracting data directly from out. 
                double gain = out.getGain();
                if (gain != 1.0)
                    for(int q = 0; q < amplitudes.length; q++)
                        amplitudes[q] *= gain;
                }
            }

        if (out != null)
            if (constrain()) 
                bigSort(0, false);
        
        if (note != sound.getNote())            // restore the note.  May have been changed by Fix
            sound.setNote(note);
        }
   
    public void setSound(Sound sound)
        {
        super.setSound(sound); 
        for(int i = 0; i < modules.length; i++)
            modules[i].setSound(sound);
        }

    /** This is called to build a Macro for the express purpose
        of serializing it out and then forgetting about it. */
    public Macro(Modulation[] modules)
        {
        super(null);    // mod hasn't been set yet
        this.modules = modules;
        
        // this fixed a difficult to track down bug.  In Macro.go() we call getOrders, and then modify it.
        // But since pushOrders just pointer-copies to orders, and by default ordersIn is NIL,
        // then Macro modifies NIL's orders.  That's not good because NIL is shared between all Sounds,
        // and thus modifying it is a race condition.  (Plus you're not supposed to modify it).
        // This breaks the RandomFatten macro, for example.
        setPushOrders(false);
        }
    
    /** This is called to build a Macro and load the sound. */
    public Macro(Sound sound, Modulation[] modules, String patchName)
        {
        super(sound);  // modules hasn't been set yet
        loadModules(modules, patchName);
        setSound(sound);  // distribute to modules

        // this fixed a difficult to track down bug.  In Macro.go() we call getOrders, and then modify it.
        // But since pushOrders just pointer-copies to orders, and by default ordersIn is NIL,
        // then Macro modifies NIL's orders.  That's not good because NIL is shared between all Sounds,
        // and thus modifying it is a race condition.  (Plus you're not supposed to modify it).
        // This breaks the RandomFatten macro, for example.
        setPushOrders(false);
        }
        
    public Macro(Sound sound)
        {
        super(sound);

        // this fixed a difficult to track down bug.  In Macro.go() we call getOrders, and then modify it.
        // But since pushOrders just pointer-copies to orders, and by default ordersIn is NIL,
        // then Macro modifies NIL's orders.  That's not good because NIL is shared between all Sounds,
        // and thus modifying it is a race condition.  (Plus you're not supposed to modify it).
        // This breaks the RandomFatten macro, for example.
        setPushOrders(false);
        }
                
    public void loadModules(Modulation[] modules, String patchName)
        {
        this.modules = modules;  // now it's set
        this.patchName  = patchName;
        
        for(int m = 0; m < modules.length; m++)
            {
            modules[m].setMacro(this);
            }
        
        // find Out
        for(int m = modules.length - 1; m >= 0; m--)
            {
            if (modules[m] instanceof Out)
                {
                // set the output
                out = (Out)(modules[m]);
                out.setMacro(this);
                
                // identify which outputs are being used so we can reduce them
                // to make the macro prettier
                for(int i = 0; i < unitOuts.length; i++)
                    {
                    unitOuts[i] = !out.isInputNil(i);
                    }
                for(int i = 0; i < modOuts.length; i++)
                    {
                    modOuts[i] = !out.isModulationConstant(i);
                    }

                break;
                }
            }
                
        // find all the Ins, including first In
        for(int m = 0; m < modules.length; m++)
            {
            if (modules[m] instanceof In)
                {
                In in = (In)modules[m];
                ins.add((In)modules[m]);
                ((In)modules[m]).setMacro(this);
                
                // identify which inputs are being used so we can reduce them
                // to make the macro prettier
                // This is more complex because we don't have back-pointers
                // so we must search through all possible inputs
                for(int i = 0; i < In.NUM_MOD_INPUTS; i++)
                    {
                    for(int j = 0; j < modules.length; j++)
                        {
                        if (modules[j] instanceof Unit)
                            {
                            Unit u = (Unit)(modules[j]);
                            for(int k = 0; k < u.getNumInputs(); k++)
                                {
                                if ( u.getInput(k) == in && u.getInputIndex(k) == i ) // found a backpointer
                                    {
                                    unitIns[u.getInputIndex(k)] = true;
                                    }
                                }
                            }
                        }
                    }

                for(int i = 0; i < In.NUM_UNIT_INPUTS; i++)
                    {
                    for(int j = 0; j < modules.length; j++)
                        {
                        Modulation mm = (Modulation)(modules[j]);
                        for(int k = 0; k < modules[j].getNumModulations(); k++)
                            {
                            if ( mm.getModulation(k) == in && mm.getModulationIndex(k) == i ) // found a backpointer
                                {
                                modIns[mm.getModulationIndex(k)] = true;
                                }
                            }
                        }
                    }
                }
            }

        // set the mod outputs
        if (out != null)
            {
            defineOutputs(out.getInputNames());
            // eliminate "Gain"
            String[] names = new String[Out.NUM_MOD_OUTPUTS];
            System.arraycopy(out.getModulationNames(), 0, names, 0, Out.NUM_MOD_OUTPUTS);
            defineModulationOutputs(names);
            }
        else
            {
            // notice that these are cloned.  This is so MOD_NAMES and UNIT_NAMES can't be changed via setModulationOutput() etc.
            // See getKeyForModulation() below for a hint as to why
            defineModulationOutputs((String[])(Out.MOD_NAMES.clone()));
            defineOutputs((String[])(Out.UNIT_NAMES.clone()));
            }
            
        // set the inputs
        if (ins.size() > 0)
            {
            // compute the default settings for each one
            Constant[] c = new Constant[] { Constant.ZERO, Constant.ZERO, Constant.ZERO, Constant.ZERO, Constant.ZERO, Constant.ZERO, Constant.ZERO, Constant.ZERO, Constant.HALF, Constant.HALF };
            for(int i = 0; i < In.NUM_MOD_INPUTS; i++)
                if (ins.get(0).getModulation(i) instanceof Constant)
                    {
                    c[i] = new Constant(ins.get(0).modulate(i));
                    }
            
            String[] s = concatenate(ins.get(0).getModulationOutputNames(), new String[] { "On Tr", "Off Tr" });
            defineModulations(c, s);
            defineInputs(new Unit[] { Unit.NIL, Unit.NIL, Unit.NIL, Unit.NIL, Unit.NIL, Unit.NIL, Unit.NIL, Unit.NIL }, 
                ins.get(0).getOutputNames());
        	if (INCLUDE_PAUSE)
        		defineOptions(new String[] { "Pause", },  new String[][] { { "Pause" } } );
            }
        else
            {        
            // notice that these are cloned.  This is so MOD_NAMES and UNIT_NAMES can't be changed via setModulationOutput() etc.
            // See getKeyForModulation() below for a hint as to why
            // It's important that Pause have Constant.ONE, else it's always pausing things
            defineModulations(new Constant[] { Constant.ZERO, Constant.ZERO, Constant.ZERO, Constant.ZERO, Constant.ZERO, Constant.ZERO, Constant.ZERO, Constant.ZERO, Constant.HALF, Constant.HALF }, 
            		concatenate((String[])(In.MOD_NAMES.clone()), new String[] { "On Tr", "Off Tr" }));
            defineInputs(new Unit[] { Unit.NIL, Unit.NIL, Unit.NIL, Unit.NIL, Unit.NIL, Unit.NIL, Unit.NIL, Unit.NIL }, (String[])(In.UNIT_NAMES.clone()));  
        	if (INCLUDE_PAUSE)
        		defineOptions(new String[] { "Pause", },  new String[][] { { "Pause" } } );
            }             
        }
        
    String[] concatenate(String[] a, String[] b)
    	{
    	String[] res = new String[a.length + b.length];
    	System.arraycopy(a, 0, res, 0, a.length);
    	System.arraycopy(b, 0, res, a.length, b.length);
    	return res;
    	}
    
    ////// Why are we overriding these three methods?  Because we want to use the standard "A B C D"
    ////// as the KEYS for connection regardless of what the user sets the actual names to.  That way
    ////// he can name the all "foo" if he likes, and they're still unique names.  
    
    public String getKeyForModulationOutput(int output)
        {
        return Out.MOD_NAMES[output];
        }
   
    public String getKeyForOutput(int output)
        {
        // Some macros I've already made and am using inside patches have a single output renamed from "A" to "Out".
        // So for backwards-compatibility with those patches, if we have a first output called "Out", that's okay.
        String storedKeyName = super.getKeyForOutput(output);
        if (output == 0 && storedKeyName.equals("Out"))
            return storedKeyName;
        return Out.UNIT_NAMES[output];
        }


    public void setData(JSONObject data, int moduleVersion, int flowVersion) throws Exception
        {
        loadModules(Sound.loadModules(data, flowVersion), Sound.loadName(data));
        }

    String info;
    String version;
    String author;
    String date;
        
    public JSONObject getData() 
        { 
        JSONObject obj = new JSONObject();
        
        Sound.saveName(patchName, obj);
        
        JSONArray array = new JSONArray();

        int id = 0;             
        int len = modules.length;
        for(int i = 0; i < len; i++)
            modules[i].setID("a" + (id++));

        for(int i = 0; i < len; i++)
            array.put(modules[i].save());
                
        obj.put("modules", array);
        return obj;
        }
    
    public static Macro loadMacro(Sound sound, JSONObject obj) throws Exception
        {
        obj.remove("sub");  // strip out subgroups
        Macro macro = new Macro(sound, 
            Sound.loadModules(obj, Sound.loadFlowVersion(obj)), 
            Sound.loadName(obj));
        macro.info = obj.optString("info", "");
        macro.version = obj.optString("v", "");
        macro.author = obj.optString("by", "");
        macro.date = obj.optString("on", "");
        return macro;
        }
        
    public static final int LABEL_MAX_LENGTH = 24;

    public ModulePanel getPanel()
        {
        final JLabel _author = new JLabel(" ", SwingConstants.LEFT);
        final JLabel _date = new JLabel(" ", SwingConstants.LEFT);
        final JLabel _version = new JLabel(" ", SwingConstants.LEFT);
        final JTextArea _info = new JTextArea(5, LABEL_MAX_LENGTH / 2);
                
        _author.setFont(Style.SMALL_FONT());
        _date.setFont(Style.SMALL_FONT());
        _version.setFont(Style.SMALL_FONT());
        _info.setFont(Style.SMALL_FONT());
        _info.setLineWrap(true);
        _info.setWrapStyleWord(true);
        _info.setBorder(null);
        _info.setBackground(_author.getBackground());
        _info.setRows(20);
        _info.setText(" ");
        _info.setHighlighter(null);
        _info.setEditable(false);
        _info.setCaretPosition(0);  // scrolls to top
                
        final JScrollPane pane = new JScrollPane(_info);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setBorder(null);
        pane.getViewport().setBackground(_author.getBackground());
        pane.getVerticalScrollBar().setBackground(_author.getBackground());
        pane.getVerticalScrollBar().setOpaque(false);
        
        return new ModulePanel(Macro.this)
            {
            public JComponent buildPanel()
                {
                Box box = new Box(BoxLayout.Y_AXIS);

                _author.setText(author);
                _date.setText(date);
                _version.setText(version);
                _info.setText(info);
                                
                Box right = new Box(BoxLayout.Y_AXIS);

                JLabel label = new JLabel("<html><b>Version</b></html>");
                label.setFont(Style.SMALL_FONT());              
                right.add(label);
                right.add(_version);
                right.add(Strut.makeVerticalStrut(3));

                label = new JLabel("<html><b>Author</b></html>");
                label.setFont(Style.SMALL_FONT());              
                right.add(label);
                right.add(_author);
                right.add(Strut.makeVerticalStrut(3));

                label = new JLabel("<html><b>Date</b></html>");
                label.setFont(Style.SMALL_FONT());              
                right.add(label);
                right.add(_date);
                right.add(Strut.makeVerticalStrut(3));

                label = new JLabel("<html><b>Info</b></html>");
                label.setFont(Style.SMALL_FONT());              
                right.add(label);
                
                JPanel disclosureP = new JPanel();
                disclosureP.setLayout(new BorderLayout());
                disclosureP.add(right, BorderLayout.NORTH);
                disclosureP.add(pane, BorderLayout.CENTER);


                Unit unit = (Unit) getModulation();
                boolean hasIns = false;
                boolean hasOuts = false;

                // To simplify things, 
                // we're going to selectively display certain inputs and outputs
                // based on whether the underlying patch has attached to them
                                
                for(int i = 0; i < unit.getNumOutputs(); i++)
                    {
                    if (unitOuts[i])
                        {
                        hasOuts = true;
                        box.add(new UnitOutput(unit, i, this));
                        }
                    }                
                for(int i = 0; i < unit.getNumInputs(); i++)
                    {
                    if (unitIns[i])
                        {
                        hasIns = true;
                        box.add(new UnitInput(unit, i, this));
                        }
                    }                
                for(int i = 0; i < unit.getNumModulationOutputs(); i++)
                    {
                    if (modOuts[i])
                        {
                        box.add(new ModulationOutput(unit, i, this));
                        }
                    }                
                for(int i = 0; i < In.NUM_MOD_INPUTS; i++)
                    {
                    if (modIns[i])
                        {
                        box.add(new ModulationInput(unit, i, this));
                        }
                    }                
                
                if (hasIns && hasOuts)
                    box.add(new ConstraintsChooser(unit, this));

				if (INCLUDE_PAUSE)
					box.add(new OptionsChooser(unit, OPTION_PAUSE));
				box.add(new ModulationInput(unit, MOD_ON_TR, this));
				box.add(new ModulationInput(unit, MOD_OFF_TR, this));
				
                JLabel disclosureLabel = new JLabel("Info  ");
                disclosureLabel.setFont(Style.SMALL_FONT());
                DisclosurePanel disclosure = new DisclosurePanel(disclosureLabel, disclosureP, null);
                box.add(disclosure);

                return box;
                }
            };
        }
    }
