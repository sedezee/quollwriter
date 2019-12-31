package com.quollwriter.ui.fx.userobjects;

import java.util.*;

import javafx.beans.property.*;

import com.gentlyweb.utils.*;

import com.quollwriter.*;
import com.quollwriter.ui.fx.*;
import com.quollwriter.ui.fx.viewers.*;
import com.quollwriter.ui.fx.components.*;
import com.quollwriter.data.*;

import static com.quollwriter.LanguageStrings.*;
import static com.quollwriter.uistrings.UILanguageStringsManager.getUILanguageStringProperty;

public class WebpageUserConfigurableObjectFieldViewEditHandler extends AbstractUserConfigurableObjectFieldViewEditHandler<WebpageUserConfigurableObjectTypeField, String>
{

    private QuollTextField editItem = null;

    public WebpageUserConfigurableObjectFieldViewEditHandler (WebpageUserConfigurableObjectTypeField typeField,
                                                              UserConfigurableObject                 obj,
                                                              UserConfigurableObjectField            field,
                                                              AbstractProjectViewer                  viewer)
    {

        super (typeField,
               obj,
               field,
               viewer);

    }

    @Override
    public void grabInputFocus ()
    {

        if (this.editItem != null)
        {

            this.editItem.requestFocus ();

        }

    }

    @Override
    public Set<Form.Item> getInputFormItems (String   initValue,
                                             Runnable formSave)
    {

        Set<Form.Item> items = new LinkedHashSet<> ();

        this.editItem = QuollTextField.builder ()
            .text (this.field != null ? (String) this.field.getValue () : initValue)
            .build ();

        UIUtils.addDoOnReturnPressed (this.editItem,
                                      formSave);

        items.add (new Form.Item (this.typeField.formNameProperty (),
                                  this.editItem));

        return items;

    }

    @Override
    public Set<StringProperty> getInputFormItemErrors ()
    {

        return null;

    }

    @Override
    public String getInputSaveValue ()
    {

        return this.editItem.getText ();

    }

    @Override
    public String valueToString (String val)
    {

        return val;

    }

    @Override
    public String stringToValue (String s)
    {

        return s;

    }

    @Override
    public Set<Form.Item> getViewFormItems ()
    {

        Set<Form.Item> items = new LinkedHashSet<> ();

        String value = (this.field != null ? (String) this.field.getValue () : null);

        if (value != null)
        {

            if ((!value.startsWith ("http://"))
                &&
                (!value.startsWith ("https://"))
               )
            {

                value = "http://" + value;

            }

            items.add (new Form.Item (this.typeField.formNameProperty (),
                                      QuollLabel2.builder ()
                                        .label (new SimpleStringProperty (value))
                                        .build ()));

        } else {

            items.add (this.createNoValueItem (getUILanguageStringProperty (form,view,types,webpage,novalue)));

        }

        return items;

    }

}
