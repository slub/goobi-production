/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.production.forms.dataeditor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.kitodo.DummyRulesetManagement;
import org.kitodo.api.dataformat.IncludedStructuralElement;
import org.kitodo.api.dataformat.mets.LinkedMetsResource;
import org.kitodo.data.database.beans.Process;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;

public class StructurePanelTest {

    @Test
    public void testBuildStructureTreeRecursively() throws Exception {
        DataEditorForm dummyDataEditorForm = new DataEditorForm();
        Process process = new Process();
        dummyDataEditorForm.setProcess(process);
        Field ruleset = DataEditorForm.class.getDeclaredField("ruleset");
        ruleset.setAccessible(true);
        ruleset.set(dummyDataEditorForm, new DummyRulesetManagement());
        final StructurePanel underTest = new StructurePanel(dummyDataEditorForm);

        IncludedStructuralElement structure = new IncludedStructuralElement();
        LinkedMetsResource link = new LinkedMetsResource();
        link.setUri(URI.create("database://?process.id=42"));
        structure.setLink(link);
        TreeNode result = new DefaultTreeNode();

        Method buildStructureTreeRecursively = StructurePanel.class.getDeclaredMethod("buildStructureTreeRecursively",
            IncludedStructuralElement.class, TreeNode.class);
        buildStructureTreeRecursively.setAccessible(true);
        buildStructureTreeRecursively.invoke(underTest, structure, result);

        Assert.assertTrue(((StructureTreeNode) result.getChildren().get(0).getData()).isLinked());
    }

    @Test
    public void testDetermineIncludedStructuralElementPathToChildRecursive() throws Exception {
        IncludedStructuralElement includedStructuralElement = new IncludedStructuralElement();
        includedStructuralElement.setType("newspaperYear");

        IncludedStructuralElement monthIncludedStructuralElement = new IncludedStructuralElement();
        monthIncludedStructuralElement.setType("newspaperMonth");

        IncludedStructuralElement wrongDayIncludedStructuralElement = new IncludedStructuralElement();
        wrongDayIncludedStructuralElement.setType("newspaperDay");
        wrongDayIncludedStructuralElement.setLabel("wrong");
        LinkedMetsResource wrongLink = new LinkedMetsResource();
        wrongLink.setUri(URI.create("database://?process.id=13"));
        wrongDayIncludedStructuralElement.setLink(wrongLink);
        monthIncludedStructuralElement.getChildren().add(wrongDayIncludedStructuralElement);

        IncludedStructuralElement correctDayIncludedStructuralElement = new IncludedStructuralElement();
        correctDayIncludedStructuralElement.setType("newspaperDay");
        correctDayIncludedStructuralElement.setLabel("correct");
        LinkedMetsResource correctLink = new LinkedMetsResource();
        correctLink.setUri(URI.create("database://?process.id=42"));
        correctDayIncludedStructuralElement.setLink(correctLink);
        monthIncludedStructuralElement.getChildren().add(correctDayIncludedStructuralElement);

        includedStructuralElement.getChildren().add(monthIncludedStructuralElement);
        int number = 42;

        Method determineIncludedStructuralElementPathToChildRecursive = StructurePanel.class.getDeclaredMethod(
            "determineIncludedStructuralElementPathToChildRecursive", IncludedStructuralElement.class, int.class);
        determineIncludedStructuralElementPathToChildRecursive.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<IncludedStructuralElement> result = (List<IncludedStructuralElement>) determineIncludedStructuralElementPathToChildRecursive
                .invoke(null, includedStructuralElement, number);

        Assert.assertEquals(new LinkedList<>(Arrays.asList(includedStructuralElement, monthIncludedStructuralElement,
            correctDayIncludedStructuralElement)), result);
    }
}