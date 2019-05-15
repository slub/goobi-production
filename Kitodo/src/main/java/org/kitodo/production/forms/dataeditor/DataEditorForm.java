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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.SessionScoped;
import javax.inject.Named;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataformat.IncludedStructuralElement;
import org.kitodo.api.dataformat.MediaUnit;
import org.kitodo.api.dataformat.View;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.validation.State;
import org.kitodo.api.validation.ValidationResult;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.services.ServiceManager;

@Named("DataEditorForm")
@SessionScoped
public class DataEditorForm implements RulesetSetupInterface, Serializable {

    /**
     * Indicates to JSF to navigate to the web page containing the meta-data
     * editor.
     */
    private static final String PAGE_METADATA_EDITOR = "/pages/metadataEditor?faces-redirect=true";
    private static final Logger logger = LogManager.getLogger(DataEditorForm.class);

    /**
     * A filter on the rule set depending on the workflow step. So far this is
     * not configurable anywhere and is therefore on “edit”.
     */
    private String acquisitionStage;

    /**
     * Backing bean for the add doc struc type dialog.
     */
    private final AddDocStrucTypeDialog addDocStrucTypeDialog;

    /**
     * Backing bean for the add MediaUnit dialog.
     */
    private final AddMediaUnitDialog addMediaUnitDialog;

    /**
     * Backing bean for the edit pages dialog.
     */
    private final EditPagesDialog editPagesDialog;

    /**
     * Backing bean for the gallery panel.
     */
    private final GalleryPanel galleryPanel;

    /**
     * The path to the main file, to save it later.
     */
    private URI mainFileUri;

    /**
     * Backing bean for the meta-data panel.
     */
    private final MetadataPanel metadataPanel;

    /**
     * Backing bean for the pagination panel.
     */
    private final PaginationPanel paginationPanel;

    /**
     * The language preference list of the editing user for displaying the
     * meta-data labels. We cache this because it’s used thousands of times and
     * otherwise the access would always go through the search engine, which
     * would delay page creation.
     */
    private List<LanguageRange> priorityList;

    /**
     * Process whose workpiece is under edit.
     */
    private Process process;

    private String referringView = "desktop";

    /**
     * The ruleset that the file is based on.
     */
    private RulesetManagementInterface ruleset;

    /**
     * Backing bean for the structure panel.
     */
    private final StructurePanel structurePanel;

    /**
     * User sitting in front of the editor.
     */
    private User user;

    /**
     * The file content.
     */
    private Workpiece workpiece;

    /**
     * Public constructor.
     */
    public DataEditorForm() {
        this.structurePanel = new StructurePanel(this);
        this.metadataPanel = new MetadataPanel(this);
        this.galleryPanel = new GalleryPanel(this);
        this.paginationPanel = new PaginationPanel(this);
        this.addDocStrucTypeDialog = new AddDocStrucTypeDialog(this);
        this.addMediaUnitDialog = new AddMediaUnitDialog(this);
        this.editPagesDialog = new EditPagesDialog(this);
        acquisitionStage = "edit";
    }

    /**
     * This method must be called to start the meta-data editor. When this
     * method is executed, the meta-data editor is not yet open in the browser,
     * but the previous page is still displayed.
     *
     * @param id
     *            ID of the process to open
     * @param referringView
     *            JSF page the user came from
     *
     * @return which page JSF should navigate to
     */
    public String open(int id, String referringView) {
        try {
            this.referringView = referringView;
            Helper.getRequestParameter("referringView");
            this.process = ServiceManager.getProcessService().getById(id);
            this.user = ServiceManager.getUserService().getCurrentUser();

            ruleset = openRulesetFile(process.getTemplate().getRuleset().getFile());
            if (!openMetsFile()) {
                return referringView;
            }
            init();
        } catch (IOException | URISyntaxException | DAOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            return referringView;
        }
        return PAGE_METADATA_EDITOR;
    }

    /**
     * Opens the METS file.
     *
     * @return whether successful. False, if the file cannot be locked.
     * @throws URISyntaxException
     *             if the file URI cannot be built (due to invalid characters in
     *             the directory path)
     * @throws IOException
     *             if filesystem I/O fails
     */
    private boolean openMetsFile() throws URISyntaxException, IOException {
        URI workPathUri = ServiceManager.getFileService().getProcessBaseUriForExistingProcess(process);
        String workDirectoryPath = workPathUri.getPath();
        mainFileUri = new URI(workPathUri.getScheme(), workPathUri.getUserInfo(), workPathUri.getHost(),
                workPathUri.getPort(), workDirectoryPath.endsWith("/") ? workDirectoryPath.concat("meta.xml")
                        : workDirectoryPath + '/' + "meta.xml",
                workPathUri.getQuery(), null);

        workpiece = ServiceManager.getMetsService().loadWorkpiece(mainFileUri);
        ServiceManager.getFileService().searchForMedia(process, workpiece);
        return true;
    }

    private RulesetManagementInterface openRulesetFile(String fileName) throws IOException {
        final long begin = System.nanoTime();
        String metadataLanguage = user.getMetadataLanguage();
        priorityList = LanguageRange.parse(metadataLanguage.isEmpty() ? "en" : metadataLanguage);
        RulesetManagementInterface ruleset = ServiceManager.getRulesetManagementService().getRulesetManagement();
        ruleset.load(new File(Paths.get(ConfigCore.getParameter(ParameterCore.DIR_RULESETS), fileName).toString()));
        if (logger.isTraceEnabled()) {
            logger.trace("Reading ruleset took {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
        }
        return ruleset;
    }

    private void init() {
        final long begin = System.nanoTime();

        structurePanel.show();
        metadataPanel.showLogical(getSelectedStructure());
        metadataPanel.showPhysical(getSelectedMediaUnit());
        galleryPanel.show();
        paginationPanel.show();

        addDocStrucTypeDialog.prepare();
        addMediaUnitDialog.prepare();
        editPagesDialog.prepare();

        if (logger.isTraceEnabled()) {
            logger.trace("Initializing editor beans took {} ms",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
        }
    }

    /**
     * Clears all remaining content from the data editor form.
     *
     * @return the referring view, to return there
     */
    public String close() {
        metadataPanel.clear();
        structurePanel.clear();
        workpiece = null;
        mainFileUri = null;
        ruleset = null;
        process = null;
        user = null;
        if (referringView.contains("?")) {
            return referringView + "&faces-redirect=true";
        } else {
            return referringView + "?faces-redirect=true";
        }
    }

    /**
     * Validate the structure and metadata.
     */
    public void validate() {
        try {
            ValidationResult validationResult = ServiceManager.getMetadataValidationService().validate(workpiece,
                ruleset);
            State state = validationResult.getState();
            if (State.ERROR.equals(state)) {
                Helper.setErrorMessage("dataEditor.validation.state.error");
                for (String message : validationResult.getResultMessages()) {
                    Helper.setErrorMessage(message);
                }
            } else {
                Helper.setMessage("dataEditor.validation.state.".concat(state.toString().toLowerCase()));
                for (String message : validationResult.getResultMessages()) {
                    Helper.setMessage(message);
                }
            }
        } catch (DataException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
    }

    /**
     * Save the structure and meta-data.
     *
     * @return navigation target
     */
    public String save() {
        try {
            metadataPanel.preserve();
            structurePanel.preserve();
            try (OutputStream out = ServiceManager.getFileService().write(mainFileUri)) {
                ServiceManager.getMetsService().save(workpiece, out);
            }
            return close();
        } catch (InvalidMetadataValueException | IOException | NoSuchMetadataFieldException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            return null;
        }
    }

    /**
     * Callback function for click on 'delete' option in logical structure tree context menu.
     */
    public void deleteButtonClick() {
        structurePanel.deleteSelectedStructure();
    }

    /**
     * Callback function for click on 'delete' option in physical structure tree context menu.
     */
    public void deleteMediaUnit() {
        structurePanel.deleteSelectedMediaUnit();
    }

    @Override
    public String getAcquisitionStage() {
        return acquisitionStage;
    }

    /**
     * Return addDocStrucTypeDialog.
     *
     * @return addDocStrucTypeDialog.
     */
    public AddDocStrucTypeDialog getAddDocStrucTypeDialog() {
        return addDocStrucTypeDialog;
    }

    /**
     * Return addMediaUnitDialog.
     *
     * @return addMediaUnitDialog
     */
    public AddMediaUnitDialog getAddMediaUnitDialog() {
        return addMediaUnitDialog;
    }

    /**
     * Return editPagesDialog.
     *
     * @return editPagesDialog
     */
    public EditPagesDialog getEditPagesDialog() {
        return editPagesDialog;
    }

    /**
     * Return galleryPanel.
     *
     * @return galleryPanel
     */
    public GalleryPanel getGalleryPanel() {
        return galleryPanel;
    }

    /**
     * Return metadataPanel.
     *
     * @return metadataPanel
     */
    public MetadataPanel getMetadataPanel() {
        return metadataPanel;
    }

    /**
     * Return paginationPanel.
     *
     * @return paginationPanel
     */
    public PaginationPanel getPaginationPanel() {
        return paginationPanel;
    }

    @Override
    public List<LanguageRange> getPriorityList() {
        return priorityList;
    }

    /**
     * Get process.
     *
     * @return value of process
     */
    Process getProcess() {
        return process;
    }

    /**
     * Get process title.
     *
     * @return value of process title
     */
    public String getProcessTitle() {
        return process.getTitle();
    }

    @Override
    public RulesetManagementInterface getRuleset() {
        return ruleset;
    }

    Optional<IncludedStructuralElement> getSelectedStructure() {
        return structurePanel.getSelectedStructure();
    }

    Optional<MediaUnit> getSelectedMediaUnit() {
        return structurePanel.getSelectedMediaUnit();
    }

    /**
     * Return structurePanel.
     *
     * @return structurePanel
     */
    public StructurePanel getStructurePanel() {
        return structurePanel;
    }

    Workpiece getWorkpiece() {
        return workpiece;
    }

    void refreshStructurePanel() {
        structurePanel.show();
    }

    void setProcess(Process process) {
        this.process = process;
    }

    void switchStructure(Object treeNodeData) throws InvalidMetadataValueException, NoSuchMetadataFieldException {
        metadataPanel.preserveLogical();
        metadataPanel.showLogical(structurePanel.getSelectedStructure());
        addDocStrucTypeDialog.prepare();
        if (Objects.nonNull(treeNodeData) && treeNodeData instanceof  StructureTreeNode) {
            StructureTreeNode structureTreeNode = (StructureTreeNode) treeNodeData;
            if (Objects.nonNull(structureTreeNode.getDataObject())) {
                if (structureTreeNode.getDataObject() instanceof IncludedStructuralElement
                        && structurePanel.getSelectedStructure().isPresent()) {
                    // Logical structure element selected
                    IncludedStructuralElement structuralElement = structurePanel.getSelectedStructure().get();
                    if (!structuralElement.getViews().isEmpty()) {
                        ArrayList<View> views = new ArrayList<>(structuralElement.getViews());
                        if (Objects.nonNull(views.get(0))) {
                            View firstView = views.get(0);
                            updatePhysicalStructureTree(firstView);
                            updateGallery(firstView);
                        }
                    }
                } else if (structureTreeNode.getDataObject() instanceof View) {
                    // Page selected in logical tree
                    updateGallery((View) structureTreeNode.getDataObject());
                    // no need to update physical tree because pages can only be clicked in logical tree if physical tree is hidden!
                }
            }
        }
    }

    void switchMediaUnit() throws InvalidMetadataValueException, NoSuchMetadataFieldException {
        metadataPanel.preservePhysical();
        metadataPanel.showPhysical(structurePanel.getSelectedMediaUnit());
        addMediaUnitDialog.prepare();
        if (structurePanel.getSelectedMediaUnit().isPresent()) {
            // update gallery
            galleryPanel.updateSelection(structurePanel.getSelectedMediaUnit().get());
            // update logical tree
            for (GalleryMediaContent galleryMediaContent : galleryPanel.getMedias()) {
                if (structurePanel.getSelectedMediaUnit().get().getMediaFiles().values().contains(galleryMediaContent.getPreviewUri())) {
                    structurePanel.updateLogicalNodeSelection(galleryMediaContent);
                    break;
                }
            }
        }
    }

    private void updatePhysicalStructureTree(View view) {
        GalleryMediaContent galleryMediaContent = this.galleryPanel.getGalleryMediaContent(view);
        if (Objects.nonNull(galleryMediaContent)) {
            structurePanel.updatePhysicalNodeSelection(galleryMediaContent);
        }
    }

    private void updateGallery(View view) {
        MediaUnit mediaUnit = view.getMediaUnit();
        if (Objects.nonNull(mediaUnit)) {
            galleryPanel.updateSelection(mediaUnit);
        }
    }
}