/*******************************************************************************
 Copyright (c) 2014,2015, Oracle and/or its affiliates. All rights reserved.
 
 $revision_history$
 19-mar-2015   Steven Davelaar
 1.3           Added call to EntityUtils.refreshCurrentEntity in refreshChildEntityList method
               to ensure UI is also refreshed correctly when child entities are shown in form layout 
 27-dec-2014   Steven Davelaar
 1.2           Overloaded method createIndirectList with additional array attr name argument
               Added method refreshChildEntityList
 17-jan-2014   Steven Davelaar
 1.1           Added method createIndirectList
 06-feb-2013   Steven Davelaar
 1.0           initial creation
******************************************************************************/
package oracle.ateam.sample.mobile.persistence.model;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.List;

import oracle.adfmf.framework.api.AdfmfJavaUtilities;
import oracle.adfmf.framework.exception.AdfException;

import oracle.ateam.sample.mobile.persistence.service.IndirectList;
import oracle.ateam.sample.mobile.persistence.service.ValueHolderInterface;
import oracle.ateam.sample.mobile.persistence.metadata.AttributeMapping;
import oracle.ateam.sample.mobile.persistence.metadata.AttributeMappingOneToMany;
import oracle.ateam.sample.mobile.persistence.metadata.AttributeMappingOneToOne;
import oracle.ateam.sample.mobile.persistence.service.ValueHolder;
import oracle.ateam.sample.mobile.persistence.util.EntityUtils;
import oracle.ateam.sample.mobile.util.ADFMobileLogger;


/**
 *  Abstract class that must be extended by all data object classes that need to be persisted, either remotely or
 *  local on mobile device in SQLite database.
 * 
 * @deprecated Use the class with same name in oracle.ateam.sample.mobile.v2.persistence.* instead
 */
public abstract class Entity extends ChangeEventSupportable
{

  private static ADFMobileLogger sLog = ADFMobileLogger.createLogger(Entity.class);
  
  private transient boolean isNewEntity = false;
  private transient boolean canonicalGetExecuted = false;

  public void setCanonicalGetExecuted(boolean canonicalGetExecuted)
  {
    this.canonicalGetExecuted = canonicalGetExecuted;
  }

  /**
   * Method does not start with "is" to prevent property from showing up in DC palette
   * @return
   */
  public boolean canonicalGetExecuted()
  {
    return canonicalGetExecuted;
  }

  public Object getAttributeValue(String attrName)
  {
    try
    {
      Method getter = EntityUtils.getGetMethod(this.getClass(),attrName);
      Object value = getter.invoke(this, null);
      return value;
    }
    catch (IllegalAccessException e)
    {
      throw new AdfException("Error invoking getter method for attribute " + attrName + " in class " +
                                 this.getClass().getName() + " " +
                                 e.getLocalizedMessage(),AdfException.ERROR);
    }
    catch (InvocationTargetException e)
    {
     throw new AdfException("Error invoking getter method for attribute " + attrName + " in class " +
                                this.getClass().getName() + " " +
                                e.getLocalizedMessage(),AdfException.ERROR);
   }
  }

  public void setAttributeValue(String attrName, Object value)
  {
    boolean valueHolder = value instanceof ValueHolderInterface;
    Method setter = EntityUtils.getSetMethod(this.getClass(),attrName,valueHolder);
    String valueType = value!=null ? value.getClass().getName() : "Null";
    if (setter == null)
    {
      throw new AdfException("No setter method found for attribute " + attrName + " in class " +
                                 this.getClass().getName(), AdfException.ERROR);
    }
    try
    {
      setter.setAccessible(true);
      setter.invoke(this, new Object[]
          { value });
    }
    catch (IllegalAccessException e)
    {
      throw new AdfException("Error invoking setter method for attribute " + attrName + " in class " +
                              this.getClass().getName() + " with value of type "+valueType+ ": " 
                               + e.getLocalizedMessage(),AdfException.ERROR);
    }
    catch (InvocationTargetException e)
    {
      throw new AdfException("Error invoking setter method for attribute " + attrName + " in class " +
                                 this.getClass().getName() + " with value of type "+valueType + ": " 
                                 + e.getTargetException().getClass().getName() + " " +
                                 e.getTargetException().getLocalizedMessage(),AdfException.ERROR);
    }
  }

  public void setIsNewEntity(boolean isNewEntity)
  {
    this.isNewEntity = isNewEntity;
  }

//  public boolean isIsNewEntity()
//  {
//    return isNewEntity;
//  }
  
  public boolean getIsNewEntity()
  {
    return isNewEntity;
  }
  
//  public void fireAttributeChangeEvent(Entity oldEntity, String attrName)
//  {
//    if (oldEntity!=null)
//    {
//      Object oldValue = oldEntity.getAttributeValue(attrName);
//      Object newValue = this.getAttributeValue(attrName);
//      getPropertyChangeSupport().firePropertyChange(attrName, oldValue, newValue);      
//      sLog.fine("Fired propertyChange event for entity attribute "+this.getClass().getName()+" "+attrName+" old value: "+oldValue+", new value: "+newValue);
//    }
//    getProviderChangeSupport().fireProviderRefresh(attrName);    
//    sLog.fine("Fired providerRefresh event for entity attribute "+this.getClass().getName()+" "+attrName);
//  }

  public boolean equals(Object obj)
  {
    if (obj.getClass()!=this.getClass())
    {
      return false;
    }
    Entity compareEntity = (Entity) obj;
    Object[] compareKey = EntityUtils.getEntityKey(compareEntity);
    Object[] thisKey = EntityUtils.getEntityKey(this); 
    return EntityUtils.compareKeys(compareKey, thisKey);
  }
  
  /**
   * Creates an IndirectList instance that encapsulates a AttributeMappingOneToMany
   * mapping so we can lazily load the list from DB when child collection is requested for the first time
   * @param accessorAttribute
   * @return
   */
  protected List createIndirectList(String accessorAttribute)
  {
    return createIndirectList(accessorAttribute,null);
  }

  /**
   * Creates an IndirectList instance that encapsulates a AttributeMappingOneToMany
   * mapping so we can lazily load the list from DB when child collection is requested for the first time
   * @param accessorAttribute
   * @param arrayAccessorAttribute used to refresh the child array when values are fetched remotely in background
   * @return
   */
  protected List createIndirectList(String accessorAttribute, String arrayAccessorAttribute)
  {
    AttributeMapping mapping = EntityUtils.findMapping(getClass(), accessorAttribute);
    if (mapping!=null && mapping instanceof AttributeMappingOneToMany)
    {
      return new IndirectList(this, (AttributeMappingOneToMany)mapping,arrayAccessorAttribute);          
    }
    // fallback:  return simple array list
    return new ArrayList();
  }

  /**
   * Creates a ValueHolder instance that encapsulates a AttributeMappingOneToOne
   * mapping so we can lazily load the row from DB when parent row is requested for the first time
   * @param accessorAttribute
   * @return
   */
  protected ValueHolderInterface createValueHolder(String accessorAttribute)
  {
    AttributeMapping mapping = EntityUtils.findMapping(getClass(), accessorAttribute);
    if (mapping!=null && mapping instanceof AttributeMappingOneToOne)
    {
      return new ValueHolder(this, (AttributeMappingOneToOne)mapping);          
    }
    return null;
  }

  /**
   * This method is called from IndirectList.buildDelegate when child rows for an entity are retrieved
   * through a remote server call executed in the background
   * @param oldList
   * @param newList
   * @param childClass
   * @param childAttribute
   */
  public void refreshChildEntityList(List oldList, List newList, Class childClass, String childAttribute)
  {
    Entity[] oldEntityArray = EntityUtils.getEntityListAsCorrectlyTypedArray(oldList, childClass);
    Entity[] newEntityArray = EntityUtils.getEntityListAsCorrectlyTypedArray(newList, childClass);
    getPropertyChangeSupport().firePropertyChange(childAttribute, oldEntityArray, newEntityArray);
    getProviderChangeSupport().fireProviderRefresh(childAttribute);
    // the above two statements do NOT refresh the UI when the UI displays a form layout instead of
    // a list view. So, we als refresh the current entity. 
    EntityUtils.refreshCurrentEntity(childAttribute,newList,getProviderChangeSupport());
    if (AdfmfJavaUtilities.isBackgroundThread())
    {
      AdfmfJavaUtilities.flushDataChangeEvent();
    }
  }

}
