/*
  Copyright 2016 Red Hat, Inc. and/or its affiliates
  and other contributors as indicated by the @author tags.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

 */

package org.keycloak.testsuite.admin.client.authorization;

import org.junit.Test;
import org.keycloak.admin.client.resource.ResourceResource;
import org.keycloak.admin.client.resource.ResourcesResource;
import org.keycloak.authorization.client.util.HttpResponseException;
import org.keycloak.representations.idm.authorization.ResourceOwnerRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ScopeRepresentation;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class ResourceManagementTest extends AbstractAuthorizationTest {

    @Test
    public void testCreate() {
        ResourceRepresentation newResource = createResource();

        assertEquals("Test Resource", newResource.getName());
        assertEquals("/test/*", newResource.getUri());
        assertEquals("test-resource", newResource.getType());
        assertEquals("icon-test-resource", newResource.getIconUri());
    }

    @Test
    public void failCreateWithSameName() {
        ResourceRepresentation newResource = createResource();

        try {
            doCreateResource(newResource);
            fail("Can not create resources with the same name and owner");
        } catch (Exception e) {
            assertEquals(HttpResponseException.class, e.getCause().getClass());
            assertEquals(409, HttpResponseException.class.cast(e.getCause()).getStatusCode());
        }

        newResource.setName(newResource.getName() + " Another");

        newResource = doCreateResource(newResource);

        assertNotNull(newResource.getId());
        assertEquals("Test Resource Another", newResource.getName());
    }

    @Test
    public void failCreateWithSameNameDifferentOwner() {
        ResourceRepresentation martaResource = createResource("Resource A", "marta", null, null, null);
        ResourceRepresentation koloResource = createResource("Resource A", "kolo", null, null, null);

        assertNotNull(martaResource.getId());
        assertNotNull(koloResource.getId());
        assertNotEquals(martaResource.getId(), koloResource.getId());

        assertEquals(2, getClientResource().authorization().resources().findByName(martaResource.getName()).size());

        List<ResourceRepresentation> martaResources = getClientResource().authorization().resources().findByName(martaResource.getName(), "marta");

        assertEquals(1, martaResources.size());
        assertEquals(martaResource.getId(), martaResources.get(0).getId());

        List<ResourceRepresentation> koloResources = getClientResource().authorization().resources().findByName(martaResource.getName(), "kolo");

        assertEquals(1, koloResources.size());
        assertEquals(koloResource.getId(), koloResources.get(0).getId());
    }

    @Test
    public void testUpdate() {
        ResourceRepresentation resource = createResource();

        resource.setType("changed");
        resource.setIconUri("changed");
        resource.setUri("changed");

        resource = doUpdateResource(resource);

        assertEquals("changed", resource.getIconUri());
        assertEquals("changed", resource.getType());
        assertEquals("changed", resource.getUri());
    }

    @Test(expected = NotFoundException.class)
    public void testDelete() {
        ResourceRepresentation resource = createResource();

        doRemoveResource(resource);

        getClientResource().authorization().resources().resource(resource.getId()).toRepresentation();
    }

    @Test
    public void testAssociateScopes() {
        ResourceRepresentation updated = createResourceWithDefaultScopes();

        assertEquals(3, updated.getScopes().size());

        assertTrue(containsScope("Scope A", updated));
        assertTrue(containsScope("Scope B", updated));
        assertTrue(containsScope("Scope C", updated));
    }

    @Test
    public void testUpdateScopes() {
        ResourceRepresentation resource = createResourceWithDefaultScopes();
        Set<ScopeRepresentation> scopes = new HashSet<>(resource.getScopes());

        assertEquals(3, scopes.size());
        assertTrue(scopes.removeIf(scopeRepresentation -> scopeRepresentation.getName().equals("Scope B")));

        resource.setScopes(scopes);

        ResourceRepresentation updated = doUpdateResource(resource);

        assertEquals(2, resource.getScopes().size());

        assertFalse(containsScope("Scope B", updated));
        assertTrue(containsScope("Scope A", updated));
        assertTrue(containsScope("Scope C", updated));

        scopes = new HashSet<>(updated.getScopes());

        assertTrue(scopes.removeIf(scopeRepresentation -> scopeRepresentation.getName().equals("Scope A")));
        assertTrue(scopes.removeIf(scopeRepresentation -> scopeRepresentation.getName().equals("Scope C")));

        updated.setScopes(scopes);

        updated = doUpdateResource(updated);

        assertEquals(0, updated.getScopes().size());
    }

    private ResourceRepresentation createResourceWithDefaultScopes() {
        ResourceRepresentation resource = createResource();

        assertEquals(0, resource.getScopes().size());

        HashSet<ScopeRepresentation> scopes = new HashSet<>();

        scopes.add(createScope("Scope A", "").toRepresentation());
        scopes.add(createScope("Scope B", "").toRepresentation());
        scopes.add(createScope("Scope C", "").toRepresentation());

        resource.setScopes(scopes);

        return doUpdateResource(resource);
    }

    private boolean containsScope(String scopeName, ResourceRepresentation resource) {
        Set<ScopeRepresentation> scopes = resource.getScopes();

        if (scopes != null) {
            for (ScopeRepresentation scope : scopes) {
                if (scope.getName().equals(scopeName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private ResourceRepresentation createResource() {
        return createResource("Test Resource", null, "/test/*", "test-resource", "icon-test-resource");
    }

    private ResourceRepresentation createResource(String name, String owner, String uri, String type, String iconUri) {
        ResourceRepresentation newResource = new ResourceRepresentation();

        newResource.setName(name);
        newResource.setUri(uri);
        newResource.setType(type);
        newResource.setIconUri(iconUri);
        newResource.setOwner(owner != null ? new ResourceOwnerRepresentation(owner) : null);

        return doCreateResource(newResource);
    }

    protected ResourceRepresentation doCreateResource(ResourceRepresentation newResource) {
        ResourcesResource resources = getClientResource().authorization().resources();

        Response response = resources.create(newResource);

        int status = response.getStatus();

        if (status != Response.Status.CREATED.getStatusCode()) {
            throw new RuntimeException(new HttpResponseException("Error", status, "", null));
        }

        ResourceRepresentation stored = response.readEntity(ResourceRepresentation.class);

        return resources.resource(stored.getId()).toRepresentation();
    }

    protected ResourceRepresentation doUpdateResource(ResourceRepresentation resource) {
        ResourcesResource resources = getClientResource().authorization().resources();
        ResourceResource existing = resources.resource(resource.getId());

        existing.update(resource);

        return resources.resource(resource.getId()).toRepresentation();
    }

    protected void doRemoveResource(ResourceRepresentation resource) {
        ResourcesResource resources = getClientResource().authorization().resources();
        resources.resource(resource.getId()).remove();
    }
}