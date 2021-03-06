/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jme3.gde.materialdefinition.editor;

import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 *
 * @author Nehon
 */
public class NodeToolBar extends javax.swing.JPanel implements ComponentListener, MouseListener {

    private final NodePanel node;

    /**
     * Creates new form NodeToolBar
     */
    @SuppressWarnings("LeakingThisInConstructor")
    public NodeToolBar(NodePanel node) {
        initComponents();
        this.node = node;
        if (node.getType() != NodePanel.NodeType.Fragment && node.getType() != NodePanel.NodeType.Vertex) {
            remove(codeButton);
        }
        node.addComponentListener(this);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        codeButton = new javax.swing.JButton();
        deleteButton = new javax.swing.JButton();

        setOpaque(false);
        java.awt.GridBagLayout layout = new java.awt.GridBagLayout();
        layout.rowHeights = new int[] {16};
        setLayout(layout);

        codeButton.setBackground(new java.awt.Color(255, 255, 255));
        codeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/jme3/gde/materialdefinition/icons/code.png"))); // NOI18N
        codeButton.setToolTipText(org.openide.util.NbBundle.getMessage(NodeToolBar.class, "NodeToolBar.codeButton.toolTipText")); // NOI18N
        codeButton.setBorder(null);
        codeButton.setBorderPainted(false);
        codeButton.setContentAreaFilled(false);
        codeButton.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        codeButton.setFocusable(false);
        codeButton.setIconTextGap(0);
        codeButton.setMaximumSize(new java.awt.Dimension(24, 24));
        codeButton.setMinimumSize(new java.awt.Dimension(24, 24));
        codeButton.setPreferredSize(new java.awt.Dimension(16, 16));
        codeButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/com/jme3/gde/materialdefinition/icons/codeHover.png"))); // NOI18N
        codeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                codeButtonActionPerformed(evt);
            }
        });
        add(codeButton, new java.awt.GridBagConstraints());

        deleteButton.setBackground(new java.awt.Color(255, 255, 255));
        deleteButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/jme3/gde/materialdefinition/icons/deleteNode.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(deleteButton, org.openide.util.NbBundle.getMessage(NodeToolBar.class, "NodeToolBar.deleteButton.text")); // NOI18N
        deleteButton.setToolTipText(org.openide.util.NbBundle.getMessage(NodeToolBar.class, "NodeToolBar.deleteButton.toolTipText")); // NOI18N
        deleteButton.setBorder(null);
        deleteButton.setBorderPainted(false);
        deleteButton.setContentAreaFilled(false);
        deleteButton.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        deleteButton.setFocusable(false);
        deleteButton.setIconTextGap(0);
        deleteButton.setMaximumSize(new java.awt.Dimension(24, 24));
        deleteButton.setMinimumSize(new java.awt.Dimension(24, 24));
        deleteButton.setPreferredSize(new java.awt.Dimension(16, 16));
        deleteButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/com/jme3/gde/materialdefinition/icons/deleteNodeHover.png"))); // NOI18N
        deleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteButtonActionPerformed(evt);
            }
        });
        add(deleteButton, new java.awt.GridBagConstraints());
    }// </editor-fold>//GEN-END:initComponents

    private void codeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_codeButtonActionPerformed
        node.edit();
    }//GEN-LAST:event_codeButtonActionPerformed

    private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
        node.delete();
    }//GEN-LAST:event_deleteButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton codeButton;
    private javax.swing.JButton deleteButton;
    // End of variables declaration//GEN-END:variables

    public void display() {
        if (getParent() == null) {
            node.getParent().add(this);
        }
        setBounds(node.getLocation().x + 5, node.getLocation().y - 18, node.getWidth() - 10, 16);
        node.getParent().setComponentZOrder(this, 0);
        setVisible(true);

    }

    public void componentResized(ComponentEvent e) {
    }

    public void componentMoved(ComponentEvent e) {
        setLocation(node.getLocation().x + 5, node.getLocation().y - 18);
    }

    public void componentShown(ComponentEvent e) {
    }

    public void componentHidden(ComponentEvent e) {
    }

    public void mouseClicked(MouseEvent e) {
        e.consume();
    }

    public void mousePressed(MouseEvent e) {
        e.consume();
    }

    public void mouseReleased(MouseEvent e) {
        e.consume();
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

}
