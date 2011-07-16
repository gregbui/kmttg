package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.decorator.Sorter;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class premiereTable {
   private String[] TITLE_cols = {"DATE", "SHOW", "CHANNEL", "DUR"};
   public JXTable TABLE = null;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   public JScrollPane scroll = null;

   premiereTable(JFrame dialog) {
      Object[][] data = {};
      TABLE = new JXTable(data, TITLE_cols);
      TABLE.setModel(new MyTableModel(data, TITLE_cols));
      TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      scroll = new JScrollPane(TABLE);
      
      // Add listener for click handling (for folder entries)
      TABLE.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               MouseClicked(e);
            }
         }
      );
      
      // Change color & font
      TableColumn tm;
      tm = TABLE.getColumnModel().getColumn(0);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
      tm = TABLE.getColumnModel().getColumn(1);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      tm = TABLE.getColumnModel().getColumn(2);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      tm = TABLE.getColumnModel().getColumn(3);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      
      // Define custom column sorting routines
      Comparator<Object> sortableComparator = new Comparator<Object>() {
         public int compare(Object o1, Object o2) {
            if (o1 instanceof sortableDate && o2 instanceof sortableDate) {
               sortableDate s1 = (sortableDate)o1;
               sortableDate s2 = (sortableDate)o2;
               long l1 = Long.parseLong(s1.sortable);
               long l2 = Long.parseLong(s2.sortable);
               if (l1 > l2) return 1;
               if (l1 < l2) return -1;
               return 0;
            }
            if (o1 instanceof sortableDuration && o2 instanceof sortableDuration) {
               sortableDuration s1 = (sortableDuration)o1;
               sortableDuration s2 = (sortableDuration)o2;
               if (s1.sortable > s2.sortable) return 1;
               if (s1.sortable < s2.sortable) return -1;
               return 0;
            }
            return 0;
         }
      };
      
      // Use custom sorting routines for certain columns
      Sorter sorter = TABLE.getColumnExt(0).getSorter();
      sorter.setComparator(sortableComparator);
      sorter = TABLE.getColumnExt(3).getSorter();
      sorter.setComparator(sortableComparator);
   }

   /**
    * Applied background color to single column of a JTable
    * in order to distinguish it apart from other columns.
    */ 
    class ColorColumnRenderer extends DefaultTableCellRenderer {
       private static final long serialVersionUID = 1L;
       Color bkgndColor;
       Font font;
       
       public ColorColumnRenderer(Color bkgnd, Font font) {
          super();
          // Center text in cells
          setHorizontalAlignment(CENTER);
          bkgndColor = bkgnd;
          this.font = font;
       }
       
       public Component getTableCellRendererComponent
           (JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) 
       {
          Component cell = super.getTableCellRendererComponent
             (table, value, isSelected, hasFocus, row, column);
     
          if (bkgndColor != null && ! isSelected)
             cell.setBackground( bkgndColor );
          
          cell.setFont(config.tableFont);
         
          return cell;
       }
    }   
    
    // Override some default table model actions
    class MyTableModel extends DefaultTableModel {
       private static final long serialVersionUID = 1L;

       public MyTableModel(Object[][] data, Object[] columnNames) {
          super(data, columnNames);
       }
       
       @SuppressWarnings("unchecked")
       // This is used to define columns as specific classes
       public Class getColumnClass(int col) {
          if (col == 1) {
             return sortableDate.class;
          }
          if (col == 4) {
             return sortableDuration.class;
          }
          return Object.class;
       } 
       
       // Set all cells uneditable
       public boolean isCellEditable(int row, int column) {        
          return false;
       }
    }

    // Pack all table columns to fit widest cell element
    public void packColumns(JXTable table, int margin) {
       //if (config.tableColAutoSize == 1) {
          for (int c=0; c<table.getColumnCount(); c++) {
              packColumn(table, c, 2);
          }
       //}
    }

    // Sets the preferred width of the visible column specified by vColIndex. The column
    // will be just wide enough to show the column head and the widest cell in the column.
    // margin pixels are added to the left and right
    // (resulting in an additional width of 2*margin pixels).
    public void packColumn(JXTable table, int vColIndex, int margin) {
        DefaultTableColumnModel colModel = (DefaultTableColumnModel)table.getColumnModel();
        TableColumn col = colModel.getColumn(vColIndex);
        int width = 0;
    
        // Get width of column header
        TableCellRenderer renderer = col.getHeaderRenderer();
        if (renderer == null) {
            renderer = table.getTableHeader().getDefaultRenderer();
        }
        Component comp = renderer.getTableCellRendererComponent(
            table, col.getHeaderValue(), false, false, 0, 0);
        width = comp.getPreferredSize().width;
    
        // Get maximum width of column data
        for (int r=0; r<table.getRowCount(); r++) {
            renderer = table.getCellRenderer(r, vColIndex);
            comp = renderer.getTableCellRendererComponent(
                table, table.getValueAt(r, vColIndex), false, false, r, vColIndex);
            width = Math.max(width, comp.getPreferredSize().width);
        }
    
        // Add margin
        width += 2*margin;
               
        // Set the width
        col.setPreferredWidth(width);
    }
    
    public int getColumnIndex(String name) {
       String cname;
       for (int i=0; i<TABLE.getColumnCount(); i++) {
          cname = (String)TABLE.getColumnModel().getColumn(i).getHeaderValue();
          if (cname.equals(name)) return i;
       }
       return -1;
    }
    
    public void clear() {
       DefaultTableModel model = (DefaultTableModel)TABLE.getModel(); 
       model.setNumRows(0);
    }

    public void AddRows(String tivoName, JSONArray data) {
       try {
          for (int i=0; i<data.length(); ++i) {
             AddRow(data.getJSONObject(i));
          }
          tivo_data.put(tivoName, data);
          packColumns(TABLE,2);
          if (config.gui.remote_gui != null)
             config.gui.remote_gui.setTivoName("premiere", tivoName);
       } catch (JSONException e) {
          log.error("premiereTable AddRows - " + e.getMessage());
       }
    }
    
    private void AddRow(JSONObject data) {
       try {
          JSONObject o = new JSONObject();
          Object[] info = new Object[TITLE_cols.length];
          String startString = data.getString("startTime");
          long start = getLongDateFromString(startString);
          long duration = data.getLong("duration")*1000;
          String title = " ";
          if (data.has("title"))
             title += string.utfString(data.getString("title"));
          if (data.has("subtitle"))
             title += " - " + string.utfString(data.getString("subtitle"));
          String channel = " ";
          if (data.has("channel")) {
             o = data.getJSONObject("channel");
             if (o.has("channelNumber"))
                channel += o.getString("channelNumber");
             if (o.has("callSign"))
                channel += "=" + o.getString("callSign");
          }
          
          info[0] = new sortableDate(data, start);
          info[1] = title;
          info[2] = channel;
          info[3] = new sortableDuration(duration, false);
          AddRow(TABLE, info);       
       } catch (JSONException e) {
          log.error("premiereTable AddRow - " + e.getMessage());
       }
    }
    
    private void AddRow(JTable table, Object[] data) {
       DefaultTableModel dm = (DefaultTableModel)table.getModel();
       dm.addRow(data);
    }
    
    public int[] GetSelectedRows() {
       int[] rows = TABLE.getSelectedRows();
       if (rows.length <= 0)
          log.error("No rows selected");
       return rows;
    }
    
    public JSONObject GetRowData(int row) {
       sortableDate s = (sortableDate) TABLE.getValueAt(row, getColumnIndex("DATE"));
       if (s != null)
          return s.json;
       return null;
    }    
    
    public String GetRowTitle(int row) {
       String s = (String) TABLE.getValueAt(row, getColumnIndex("SHOW"));
       if (s != null)
          return s;
       return null;
    }
    
    public void RemoveRow(JXTable table, int row) {
       DefaultTableModel dm = (DefaultTableModel)table.getModel();
       dm.removeRow(row);
    }
    
    // Mouse event handler
    // This will display additional show information when entry single-clicked
    private void MouseClicked(MouseEvent e) {
       if( e.getClickCount() == 1 ) {
          try {
             int row = TABLE.rowAtPoint(e.getPoint());
             sortableDate s = (sortableDate)TABLE.getValueAt(row,getColumnIndex("DATE"));
             sortableDuration dur = (sortableDuration)TABLE.getValueAt(row,getColumnIndex("DUR"));
             JSONObject o;
             String channelNum = null;
             String channel = null;
             if (s.json.has("channel")) {
                o = s.json.getJSONObject("channel");
                if ( o.has("channelNumber") ) {
                   channelNum = o.getString("channelNumber");
                }
                if ( o.has("callSign") ) {
                   channel = o.getString("callSign");
                }
             }
             String description = null;
             if ( s.json.has("description") ) {
                description = string.utfString(s.json.getString("description"));
             }
             String d = "";
             if (dur.sortable != null) {
                d = rnpl.msecsToMins(dur.sortable);
             }
             String message = "";
             if (s.display != null)
                message = s.display;
             if (channelNum != null && channel != null) {
                message += " on " + channelNum + "=" + channel;
             }
             message += ", Duration = " + d;
             
             if (description != null) {
                message += "\n" + description;
             }
       
             String title = "\nShow Premiere: ";
             if (s.json.has("title"))
                title += string.utfString(s.json.getString("title"));
             if (s.json.has("subtitle"))
                title += " - " + string.utfString(s.json.getString("subtitle"));
             log.warn(title);
             log.print(message);
          } catch (JSONException e1) {
             log.error("MouseClicked - " + e1.getMessage());
             return;
          }
       }
    }

    private long getLongDateFromString(String date) {
       try {
          SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
          Date d = format.parse(date + " GMT");
          return d.getTime();
       } catch (ParseException e) {
         log.error("premiereTable getLongDate - " + e.getMessage());
         return 0;
       }
    }
}