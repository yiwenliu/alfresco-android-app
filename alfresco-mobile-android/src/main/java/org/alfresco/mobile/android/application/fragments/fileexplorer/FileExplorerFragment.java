/*******************************************************************************
 * Copyright (C) 2005-2014 Alfresco Software Limited.
 *
 * This file is part of Alfresco Mobile for Android.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.alfresco.mobile.android.application.fragments.fileexplorer;

import java.io.File;
import java.util.Map;

import org.alfresco.mobile.android.application.R;
import org.alfresco.mobile.android.application.fragments.builder.AlfrescoFragmentBuilder;
import org.alfresco.mobile.android.application.fragments.create.DocumentTypesDialogFragment;
import org.alfresco.mobile.android.application.fragments.fileexplorer.FileActions.onFinishModeListerner;
import org.alfresco.mobile.android.application.fragments.utils.OpenAsDialogFragment;
import org.alfresco.mobile.android.application.intent.RequestCode;
import org.alfresco.mobile.android.application.managers.ActionUtils;
import org.alfresco.mobile.android.application.managers.DataProtectionManagerImpl;
import org.alfresco.mobile.android.async.file.browse.FilesEvent;
import org.alfresco.mobile.android.async.file.create.CreateDirectoryEvent;
import org.alfresco.mobile.android.async.file.delete.DeleteFileEvent;
import org.alfresco.mobile.android.async.file.encryption.FileProtectionEvent;
import org.alfresco.mobile.android.async.file.update.RenameFileEvent;
import org.alfresco.mobile.android.platform.AlfrescoNotificationManager;
import org.alfresco.mobile.android.platform.accounts.AlfrescoAccount;
import org.alfresco.mobile.android.platform.extensions.ScanSnapManager;
import org.alfresco.mobile.android.platform.intent.BaseActionUtils.ActionManagerListener;
import org.alfresco.mobile.android.platform.io.AlfrescoStorageManager;
import org.alfresco.mobile.android.platform.utils.AndroidVersion;
import org.alfresco.mobile.android.platform.utils.SessionUtils;
import org.alfresco.mobile.android.ui.fragments.WaitingDialogFragment;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.GridView;

import com.squareup.otto.Subscribe;

/**
 * LocalFileBrowserFragment is responsible to display the content of Download
 * Folder.
 * 
 * @author Jean Marie Pascal
 */
public class FileExplorerFragment extends FileExplorerFoundationFragment implements FileExplorerFragmentTemplate
{
    public static final String TAG = FileExplorerFragment.class.getName();

    private File privateFolder;

    private FileActions nActions;

    private File createFile;

    private boolean isShortCut = false;

    private long lastModifiedDate;

    private int menuId;

    private static final String ARGUMENT_MENU_ID = "menuId";

    private static final String ARGUMENT_SHORTCUT = "shortcut";

    // ///////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS & HELPERS
    // ///////////////////////////////////////////////////////////////////////////
    public FileExplorerFragment()
    {
        emptyListMessageId = R.string.empty_download;
        setHasOptionsMenu(true);
    }

    public static FileExplorerFragment newInstanceByTemplate(Bundle b)
    {
        FileExplorerFragment cbf = new FileExplorerFragment();
        cbf.setArguments(b);
        return cbf;
    }

    // ///////////////////////////////////////////////////////////////////////////
    // LIFECYCLE
    // ///////////////////////////////////////////////////////////////////////////
    protected void onRetrieveParameters(Bundle bundle)
    {
        super.onRetrieveParameters(bundle);
        isShortCut = getArguments().getBoolean(ARGUMENT_SHORTCUT);
        menuId = getArguments().getInt(ARGUMENT_MENU_ID);
    }

    @Override
    @Subscribe
    public void onResult(FilesEvent event)
    {
        super.onResult(event);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        AlfrescoAccount acc = SessionUtils.getAccount(getActivity());
        Bundle b = getArguments();
        if (b == null)
        {
            if (acc != null)
            {
                parent = AlfrescoStorageManager.getInstance(getActivity()).getDownloadFolder(acc);
                if (parent == null)
                {
                    AlfrescoNotificationManager.getInstance(getActivity()).showLongToast(
                            getString(R.string.sdinaccessible));
                    return;
                }
            }
            else
            {
                AlfrescoNotificationManager.getInstance(getActivity()).showLongToast(getString(R.string.loginfirst));
                return;
            }
        }
        privateFolder = AlfrescoStorageManager.getInstance(getActivity()).getRootPrivateFolder().getParentFile();

        getActivity().getActionBar().show();
        if (isShortCut)
        {
            enableTitle = false;
            FileExplorerHelper.displayNavigationMode(getActivity(), getMode(), false, menuId);
            getActivity().getActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // If the fragment is resumed after user content creation action, we
        // have to check if the file has been modified or not. Depending on
        // result we prompt the upload dialog or we do nothing (no modification
        // / blank file)
        if (createFile != null)
        {
            if (createFile.length() > 0 && lastModifiedDate < createFile.lastModified())
            {
                refresh();
            }
            else
            {
                createFile.delete();
            }
        }

        getActivity().invalidateOptionsMenu();
        refreshListView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode)
        {
            case RequestCode.CREATE:
                if (createFile != null)
                {
                    if (createFile.length() > 0 && lastModifiedDate < createFile.lastModified())
                    {
                        refresh();
                    }
                    else
                    {
                        createFile.delete();
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onStop()
    {
        if (nActions != null)
        {
            nActions.finish();
        }
        super.onStop();
    }

    // //////////////////////////////////////////////////////////////////////
    // LIST ACTIONS
    // //////////////////////////////////////////////////////////////////////
    @Override
    public void onListItemClick(GridView l, View v, int position, long id)
    {
        final File file = (File) l.getItemAtPosition(position);

        if (getMode() == MODE_PICK)
        {
            if (nActions != null)
            {
                nActions.selectFile(file);
                adapter.notifyDataSetChanged();
            }
            else
            {
                if (file.isDirectory())
                {
                    displayNavigation(file, true);
                }
                else
                {
                    Intent pickResult = new Intent();
                    pickResult.setData(Uri.fromFile(file));
                    getActivity().setResult(Activity.RESULT_OK, pickResult);
                    getActivity().finish();
                }
            }
            return;
        }

        Boolean hideDetails = false;
        if (!selectedItems.isEmpty())
        {
            hideDetails = selectedItems.get(0).getPath().equals(file.getPath());
        }
        l.setItemChecked(position, true);

        if (nActions != null)
        {
            nActions.selectFile(file);
            if (selectedItems.size() == 0)
            {
                hideDetails = true;
            }
        }
        else
        {
            selectedItems.clear();
        }

        if (hideDetails)
        {
            return;
        }
        else if (nActions == null)
        {
            if (file.isDirectory())
            {
                displayNavigation(file, true);
            }
            else
            {
                ActionUtils.actionView(this, file, new ActionManagerListener()
                {
                    @Override
                    public void onActivityNotFoundException(ActivityNotFoundException e)
                    {
                        OpenAsDialogFragment.newInstance(file).show(getActivity().getFragmentManager(),
                                OpenAsDialogFragment.TAG);
                    }
                });
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onListItemLongClick(GridView l, View v, int position, long id)
    {
        if (nActions != null) { return false; }

        File item = (File) l.getItemAtPosition(position);

        selectedItems.clear();
        selectedItems.add(item);

        // Start the CAB using the ActionMode.Callback defined above
        nActions = new FileActions(FileExplorerFragment.this, selectedItems);
        nActions.setOnFinishModeListerner(new onFinishModeListerner()
        {
            @Override
            public void onFinish()
            {
                nActions = null;
                selectedItems.clear();
                refreshListView();
            }
        });
        getActivity().startActionMode(nActions);
        adapter.notifyDataSetChanged();

        return true;
    }

    private void displayNavigation(File file, boolean backstack)
    {
        if (getMode() == MODE_PICK)
        {
            FileExplorerFragment.with(getActivity()).file(file).menuId(menuId).mode(getMode()).isShortCut(true)
                    .display();
        }
        else
        {
            FileExplorerFragment.with(getActivity()).file(file).display();
        }
    }

    // //////////////////////////////////////////////////////////////////////
    // MENU
    // //////////////////////////////////////////////////////////////////////
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);
        if (mode == MODE_LISTING)
        {

            if (parent != null && privateFolder != null && !parent.getPath().startsWith(privateFolder.getPath()))
            {
                MenuItem mi = menu.add(Menu.NONE, R.id.menu_create_folder, Menu.FIRST, R.string.folder_create);
                mi.setIcon(R.drawable.ic_add_folder);
                mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            }

            SubMenu createMenu = menu.addSubMenu(Menu.NONE, R.id.menu_device_capture, Menu.FIRST + 30,
                    R.string.add_menu);
            createMenu.setIcon(android.R.drawable.ic_menu_add);
            createMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

            createMenu.add(Menu.NONE, R.id.menu_create_document, Menu.FIRST + 1, R.string.create_document);

            createMenu.add(Menu.NONE, R.id.menu_device_capture_camera_photo, Menu.FIRST + 1, R.string.take_photo);

            if (AndroidVersion.isICSOrAbove())
            {
                createMenu.add(Menu.NONE, R.id.menu_device_capture_camera_video, Menu.FIRST + 2, R.string.make_video);
            }

            if (ScanSnapManager.getInstance(getActivity()) != null
                    && ScanSnapManager.getInstance(getActivity()).hasScanSnapApplication())
            {
                createMenu.add(Menu.NONE, R.id.menu_scan_document, Menu.FIRST + 4, R.string.scan);
            }

            createMenu.add(Menu.NONE, R.id.menu_device_capture_mic_audio, Menu.FIRST + 3, R.string.record_audio);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.menu_create_folder:
                createFolder();
                return true;
            case R.id.menu_create_document:
                DocumentTypesDialogFragment dialogft = DocumentTypesDialogFragment.newInstance(
                        SessionUtils.getAccount(getActivity()), TAG);
                dialogft.show(getFragmentManager(), DocumentTypesDialogFragment.TAG);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // //////////////////////////////////////////////////////////////////////
    // UTILS
    // //////////////////////////////////////////////////////////////////////
    public void setCreateFile(File newFile)
    {
        this.createFile = newFile;
        this.lastModifiedDate = newFile.lastModified();
    }

    public void selectAll()
    {
        if (nActions != null && adapter != null)
        {
            nActions.selectFiles(((FileExplorerAdapter) adapter).getFiles());
            adapter.notifyDataSetChanged();
        }
    }

    public File getParent()
    {
        return parent;
    }

    /**
     * Remove a site object inside the listing without requesting an HTTP call.
     */
    public void remove(File file)
    {
        if (adapter != null)
        {
            ((FileExplorerAdapter) adapter).remove(file.getPath());
            if (adapter.isEmpty())
            {
                displayEmptyView();
            }
        }
    }

    public void createFolder()
    {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag(FileNameDialogFragment.TAG);
        if (prev != null)
        {
            ft.remove(prev);
        }

        // Create and show the dialog.
        FileNameDialogFragment.newInstance(getParent()).show(ft, FileNameDialogFragment.TAG);

    }

    // //////////////////////////////////////////////////////////////////////
    // EVENTS RECEIVER
    // //////////////////////////////////////////////////////////////////////
    @Subscribe
    public void onCreateDirectoryEvent(CreateDirectoryEvent event)
    {
        if (event.hasException) { return; }
        if (event.parentFolder.equals(getParent().getPath()))
        {
            ((FileExplorerAdapter) adapter).replaceFile(event.data);
        }
        refresh();
        gv.setSelection(selectedPosition);
    }

    @Subscribe
    public void onDeleteFileEvent(DeleteFileEvent event)
    {
        if (event.hasException) { return; }
        remove(event.data);
        refresh();
        gv.setSelection(selectedPosition);
    }

    @Subscribe
    public void onUpdateFileEvent(RenameFileEvent event)
    {
        if (event.hasException) { return; }
        if (event.parentFolder.equals(getParent().getPath()))
        {
            remove(event.originalFile);
            ((FileExplorerAdapter) adapter).replaceFile(event.data);
        }
        refresh();
        gv.setSelection(selectedPosition);
    }

    @Subscribe
    public void onFileProtectionEvent(FileProtectionEvent event)
    {
        if (event.hasException) { return; }
        if (getFragment(WaitingDialogFragment.TAG) != null)
        {
            ((DialogFragment) getFragment(WaitingDialogFragment.TAG)).dismiss();
        }
        if (!event.encryptionAction)
        {
            DataProtectionManagerImpl.getInstance(getActivity()).executeAction(getActivity(), event.intentAction,
                    event.protectedFile);
        }
        refresh();
    }

    private Fragment getFragment(String tag)
    {
        return getActivity().getFragmentManager().findFragmentByTag(tag);
    }

    // ///////////////////////////////////////////////////////////////////////////
    // BUILDER
    // ///////////////////////////////////////////////////////////////////////////
    public static Builder with(Activity activity)
    {
        return new Builder(activity);
    }

    public static class Builder extends AlfrescoFragmentBuilder
    {
        public static final int ICON_ID = R.drawable.ic_download_dark;

        public static final int LABEL_ID = R.string.menu_local_files;

        // ///////////////////////////////////////////////////////////////////////////
        // CONSTRUCTORS
        // ///////////////////////////////////////////////////////////////////////////
        public Builder(Activity activity)
        {
            super(activity);
            this.extraConfiguration = new Bundle();
        }

        public Builder(Activity appActivity, Map<String, Object> configuration)
        {
            super(appActivity, configuration);
            menuIconId = ICON_ID;
            menuTitleId = LABEL_ID;
            templateArguments = new String[] { ARGUMENT_FILE, ARGUMENT_PATH, ARGUMENT_SHORTCUT, ARGUMENT_MENU_ID };
        }

        // ///////////////////////////////////////////////////////////////////////////
        // SETTERS
        // ///////////////////////////////////////////////////////////////////////////
        public Builder file(File parentFolder)
        {
            extraConfiguration.putSerializable(ARGUMENT_FILE, parentFolder);
            return this;
        }

        public Builder mode(int displayMode)
        {
            extraConfiguration.putInt(ARGUMENT_MODE, displayMode);
            return this;
        }

        public Builder isShortCut(boolean isShortCut)
        {
            extraConfiguration.putBoolean(ARGUMENT_SHORTCUT, isShortCut);
            return this;
        }

        public Builder menuId(int menuId)
        {
            extraConfiguration.putInt(ARGUMENT_MENU_ID, menuId);
            return this;
        }

        protected Fragment createFragment(Bundle b)
        {
            return newInstanceByTemplate(b);
        }

    }
}
