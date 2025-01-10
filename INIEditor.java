import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.DefaultListModel;
import javax.swing.ListCellRenderer;

public class INIEditor extends JFrame {
    private final JTabbedPane tabbedPane;
    private static final Dimension TEXT_FIELD_SIZE = new Dimension(110, 20);
    private File lastSelectedFolder; // Remember the last selected folder
    private static final String PREF_KEY_DEFAULT_FOLDER = "defaultFolder";
    private static final String PREF_KEY_RECENT_FOLDERS = "recentFolders";
    private static final int MAX_RECENT_FOLDERS = 5;
    private final Preferences prefs;

    public INIEditor() {
        setTitle("INI Editor");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        prefs = Preferences.userNodeForPackage(INIEditor.class);

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        add(tabbedPane, BorderLayout.CENTER);

        JButton selectFolderButton = new JButton("Select Folder");
        selectFolderButton.addActionListener(e -> selectFolderAndScan());

        JButton saveAllButton = new JButton("Save All");
        saveAllButton.addActionListener(e -> saveAllINIFiles());

        JButton saveButton = new JButton("Save Current Tab");
        saveButton.addActionListener(e -> saveCurrentTab());

        JButton makeDefaultFolderButton = new JButton("Make Default Folder");
        makeDefaultFolderButton.addActionListener(e -> makeDefaultFolder());

        JButton recentFoldersButton = new JButton("Recent Folders");
        recentFoldersButton.addActionListener(e -> showRecentFoldersDialog());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(selectFolderButton);
        buttonPanel.add(saveAllButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(makeDefaultFolderButton);
        buttonPanel.add(recentFoldersButton);
        add(buttonPanel, BorderLayout.SOUTH);

        loadDefaultFolder();
    }

    private void makeDefaultFolder() {
        if (lastSelectedFolder != null) {
            prefs.put(PREF_KEY_DEFAULT_FOLDER, lastSelectedFolder.getAbsolutePath());
            addRecentFolder(lastSelectedFolder.getAbsolutePath());
            JOptionPane.showMessageDialog(this, "Default folder set to: " + lastSelectedFolder.getAbsolutePath(), "Success", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "No folder selected to set as default.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadDefaultFolder() {
        String folderPath = prefs.get(PREF_KEY_DEFAULT_FOLDER, null);
        if (folderPath != null) {
            File folder = new File(folderPath);
            if (folder.isDirectory()) {
                lastSelectedFolder = folder;
                clearTabs();
                new ScanFolderWorker(folder, tabbedPane, this).execute();
            }
        }
    }

    private void addRecentFolder(String folderPath) {
        Deque<String> recentFolders = new ArrayDeque<>(MAX_RECENT_FOLDERS);
        String[] storedFolders = prefs.get(PREF_KEY_RECENT_FOLDERS, "").split(";");
        for (String storedFolder : storedFolders) {
            if (!storedFolder.isBlank() && !storedFolder.equals(folderPath)) {
                recentFolders.add(storedFolder);
            }
        }
        recentFolders.addFirst(folderPath);
        while (recentFolders.size() > MAX_RECENT_FOLDERS) {
            recentFolders.removeLast();
        }
        prefs.put(PREF_KEY_RECENT_FOLDERS, String.join(";", recentFolders));
    }

    private void showRecentFoldersDialog() {
        String[] recentFolders = prefs.get(PREF_KEY_RECENT_FOLDERS, "").split(";");
        if (recentFolders.length == 0 || recentFolders[0].isBlank()) {
            JOptionPane.showMessageDialog(this, "No recent folders available.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "Recent Folders", true);
        dialog.setSize(400, 300);
        dialog.setLayout(new BorderLayout());

        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String folder : recentFolders) {
            listModel.addElement(folder);
        }

        JList<String> folderList = new JList<>(listModel);
        folderList.setCellRenderer(new RecentFolderRenderer(folderList, listModel));
        folderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        folderList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    String selectedFolder = folderList.getSelectedValue();
                    if (selectedFolder != null) {
                        File folder = new File(selectedFolder);
                        if (folder.isDirectory()) {
                            lastSelectedFolder = folder;
                            clearTabs();
                            new ScanFolderWorker(folder, tabbedPane, INIEditor.this).execute();
                            dialog.dispose();
                        } else {
                            JOptionPane.showMessageDialog(INIEditor.this, "Selected folder does not exist.", "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(folderList);
        dialog.add(scrollPane, BorderLayout.CENTER);

        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private class RecentFolderRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel folderLabel;
        private final JButton closeButton;
        private final JList<String> folderList;
        private final DefaultListModel<String> listModel;

        public RecentFolderRenderer(JList<String> folderList, DefaultListModel<String> listModel) {
            this.folderList = folderList;
            this.listModel = listModel;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            folderLabel = new JLabel();
            closeButton = new JButton("x");
            closeButton.setOpaque(false);
            closeButton.setContentAreaFilled(false);
            closeButton.setBorderPainted(false);
            closeButton.setFocusPainted(false);
            closeButton.setMargin(new Insets(0, 5, 0, 0));
            closeButton.setForeground(Color.BLACK);
            closeButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    closeButton.setForeground(Color.RED);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    closeButton.setForeground(Color.BLACK);
                }
            });
            closeButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int index = folderList.getSelectedIndex();
                    if (index != -1) {
                        listModel.remove(index);
                        updateRecentFolders();
                    }
                }
            });
            add(folderLabel);
            add(Box.createHorizontalGlue());
            add(closeButton);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            folderLabel.setText(value);
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            return this;
        }

        private void updateRecentFolders() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < listModel.size(); i++) {
                if (i > 0) {
                    sb.append(";");
                }
                sb.append(listModel.get(i));
            }
            prefs.put(PREF_KEY_RECENT_FOLDERS, sb.toString());
        }
    }

    private void clearTabs() {
        tabbedPane.removeAll();
    }

    private void selectFolderAndScan() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (lastSelectedFolder != null) {
            fileChooser.setCurrentDirectory(lastSelectedFolder); // Set the last selected folder
        }
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File folder = fileChooser.getSelectedFile();
            lastSelectedFolder = folder; // Remember the selected folder
            addRecentFolder(folder.getAbsolutePath()); // Add to recent folders
            clearTabs();
            new ScanFolderWorker(folder, tabbedPane, this).execute();
        }
    }

    private static class ScanFolderWorker extends SwingWorker<Void, File> {
        private final File folder;
        private final JTabbedPane tabbedPane;
        private final INIEditor editor;

        public ScanFolderWorker(File folder, JTabbedPane tabbedPane, INIEditor editor) {
            this.folder = folder;
            this.tabbedPane = tabbedPane;
            this.editor = editor;
        }

        @Override
        protected Void doInBackground() throws Exception {
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".ini"));
            if (files != null) {
                for (File file : files) {
                    publish(file);
                }
            }
            return null;
        }

        @Override
        protected void process(List<File> chunks) {
            for (File file : chunks) {
                addINIFileTab(file, tabbedPane, editor);
            }
        }

        @Override
        protected void done() {
            tabbedPane.revalidate();
        }
    }

    private static void addINIFileTab(File file, JTabbedPane tabbedPane, INIEditor editor) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16); // Increase vertical scroll speed
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16); // Increase horizontal scroll speed

        tabbedPane.addTab(file.getName(), scrollPane);
        int tabIndex = tabbedPane.getTabCount() - 1;
        tabbedPane.putClientProperty("file_" + tabIndex, file);
        tabbedPane.setTabComponentAt(tabIndex, createTabComponent(tabbedPane, file.getName()));
        editor.new LoadINIFileWorker(file, panel).execute();
    }

    private static JPanel createTabComponent(JTabbedPane tabbedPane, String title) {
        JPanel tabComponent = new JPanel();
        tabComponent.setOpaque(false);
        tabComponent.setLayout(new BoxLayout(tabComponent, BoxLayout.X_AXIS));

        JLabel titleLabel = new JLabel(title);
        tabComponent.add(titleLabel);

        JButton closeButton = new JButton("x");
        closeButton.setOpaque(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setBorderPainted(false);
        closeButton.setFocusPainted(false);
        closeButton.setMargin(new Insets(0, 5, 0, 0));
        closeButton.setForeground(Color.BLACK);
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setForeground(Color.RED);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setForeground(Color.BLACK);
            }
        });
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int index = tabbedPane.indexOfTabComponent(tabComponent);
                if (index != -1) {
                    tabbedPane.remove(index);
                }
            }
        });
        tabComponent.add(closeButton);

        return tabComponent;
    }

    private class LoadINIFileWorker extends SwingWorker<Void, Void> {
        private final File file;
        private final JPanel panel;

        public LoadINIFileWorker(File file, JPanel panel) {
            this.file = file;
            this.panel = panel;
        }

        @Override
        protected Void doInBackground() throws Exception {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                int row = 0;
                String description;

                GridBagConstraints gbc = new GridBagConstraints();
                gbc.insets = new Insets(2, 2, 2, 2);
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.anchor = GridBagConstraints.WEST;
                gbc.weightx = 0;
                gbc.gridy = 0;

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(";")) {
                        description = line.substring(1).trim();
                        continue;
                    }
                    if (line.isBlank()) {
                        continue;
                    }
                    if (line.startsWith("[")) {
                        String section = line.trim();
                        gbc.gridx = 0;
                        gbc.gridwidth = 3;
                        gbc.weightx = 1;
                        gbc.insets = new Insets(10, 2, 2, 2); // Add more space above the section
                        JLabel sectionLabel = new JLabel(section);
                        sectionLabel.setFont(sectionLabel.getFont().deriveFont(Font.BOLD, sectionLabel.getFont().getSize() * 2)); // Double the font size
                        sectionLabel.setOpaque(true);
                        sectionLabel.setBackground(Color.DARK_GRAY); // Set dark background
                        sectionLabel.setForeground(Color.WHITE); // Set text color to white
                        panel.add(sectionLabel, gbc);
                        row++;
                        gbc.gridy = row;
                        gbc.gridwidth = 1;
                        gbc.insets = new Insets(2, 2, 2, 2); // Reset insets
                        continue;
                    }
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String valuePart = parts[1].trim();
                        String value;
                        description = null;

                        if (valuePart.contains(";")) {
                            String[] valueDescription = valuePart.split(";", 2);
                            value = valueDescription[0].trim();
                            description = valueDescription[1].trim();
                        } else if (valuePart.contains("//")) {
                            String[] valueDescription = valuePart.split("//", 2);
                            value = valueDescription[0].trim();
                            description = valueDescription[1].trim();
                        } else {
                            value = valuePart;
                        }

                        gbc.gridx = 0;
                        gbc.insets = new Insets(10, 2, 2, 2); // Add more space above the key-value pair
                        JLabel keyLabel = new JLabel(key + ":");
                        panel.add(keyLabel, gbc);

                        gbc.gridx = 1;
                        gbc.weightx = 1;
                        JTextField textField = new JTextField(value.length());
                        textField.putClientProperty("key", key);
                        textField.setText(value); // Set the initial value
                        int maxWidth = 300; // Set a maximum width for the text field
                        textField.setPreferredSize(new Dimension(Math.min(maxWidth, textField.getPreferredSize().width), TEXT_FIELD_SIZE.height));
                        textField.addFocusListener(new java.awt.event.FocusAdapter() {
                            @Override
                            public void focusGained(java.awt.event.FocusEvent e) {
                                textField.selectAll(); // Auto-select all text when the textbox is clicked
                            }
                        });
                        panel.add(textField, gbc);

                        gbc.gridx = 2;
                        gbc.weightx = 0;
                        JLabel descriptionLabel = new JLabel(description != null ? description : "");
                        panel.add(descriptionLabel, gbc);

                        row++;
                        gbc.gridy = row;
                        gbc.insets = new Insets(2, 2, 2, 2); // Reset insets
                    }
                }

                gbc.weighty = 1;
                panel.add(new JLabel(), gbc);
            }
            return null;
        }

        @Override
        protected void done() {
            panel.revalidate();
        }
    }

    private void saveAllINIFiles() {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component component = tabbedPane.getComponentAt(i);
            if (component instanceof JScrollPane scrollPane) {
                JPanel panel = (JPanel) scrollPane.getViewport().getView();
                File file = (File) tabbedPane.getClientProperty("file_" + i);
                if (file != null) {
                    new SavePanelFieldsWorker(panel, file).execute();
                }
            }
        }
    }

    private void saveCurrentTab() {
        int selectedIndex = tabbedPane.getSelectedIndex();
        if (selectedIndex != -1) {
            Component component = tabbedPane.getComponentAt(selectedIndex);
            if (component instanceof JScrollPane scrollPane) {
                JPanel panel = (JPanel) scrollPane.getViewport().getView();
                File file = (File) tabbedPane.getClientProperty("file_" + selectedIndex);
                if (file != null) {
                    new SavePanelFieldsWorker(panel, file).execute();
                }
            }
        }
    }

    private class SavePanelFieldsWorker extends SwingWorker<Void, Void> {
        private final JPanel panel;
        private final File file;

        public SavePanelFieldsWorker(JPanel panel, File file) {
            this.panel = panel;
            this.file = file;
        }

        @Override
        protected Void doInBackground() throws Exception {
            Map<String, String> updatedValues = new HashMap<>();
            for (Component component : panel.getComponents()) {
                if (component instanceof JTextField textField) {
                    String key = (String) textField.getClientProperty("key");
                    String value = textField.getText();
                    updatedValues.put(key, value);
                }
            }

            // Read the existing content of the file
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }

            // Write the updated content back to the file
            try (FileWriter writer = new FileWriter(file)) {
                for (String line : lines) {
                    if (line.startsWith(";") || line.isBlank() || line.startsWith("[")) {
                        writer.write(line + "\n");
                        continue;
                    }
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String valuePart = parts[1].trim();
                        String description = null;

                        if (valuePart.contains(";")) {
                            String[] valueDescription = valuePart.split(";", 2);
                            valuePart = valueDescription[0].trim();
                            description = valueDescription[1].trim();
                        } else if (valuePart.contains("//")) {
                            String[] valueDescription = valuePart.split("//", 2);
                            valuePart = valueDescription[0].trim();
                            description = valueDescription[1].trim();
                        }

                        if (updatedValues.containsKey(key)) {
                            writer.write(key + "=" + updatedValues.get(key));
                            if (description != null) {
                                writer.write(" ;" + description);
                            }
                            writer.write("\n");
                        } else {
                            writer.write(line + "\n");
                        }
                    } else {
                        writer.write(line + "\n");
                    }
                }
            }
            return null;
        }

        @Override
        protected void done() {
            try {
                get();
                JOptionPane.showMessageDialog(INIEditor.this, "File saved successfully: " + file.getName(), "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (InterruptedException | ExecutionException e) {
                JOptionPane.showMessageDialog(INIEditor.this, "Error saving file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            INIEditor editor = new INIEditor();
            editor.setVisible(true);
        });
    }
}
