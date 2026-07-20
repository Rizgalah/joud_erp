package com.joud.core.api;

import com.joud.core.service.BaseCrudService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * BaseCrudResource — السلف المشترك لجميع واجهات الـ REST API.
 * يرث الابن كافة دوال الـ API تلقائياً بمجرد تحديد الـ Path والصلاحيات.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public abstract class BaseCrudResource<T, ID> {

    // نطلب من الكلاس الابن تزويدنا بالخدمة الخاصة به
    protected abstract BaseCrudService<T, ID> getService();

    @GET
    public List<T> getAll() {
        return getService().findAll();
    }

    @GET
    @Path("/{id}")
    public T getById(@PathParam("id") ID id) {
        T entity = getService().findById(id);
        if (entity == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return entity;
    }

    @POST
    public Response create(T entity) {
        T created = getService().save(entity);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") ID id, T entity) {
        // يمكنك هنا وضع كود للتحقق من تطابق الـ id مع الكائن
        T updated = getService().update(entity);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") ID id) {
        getService().delete(id);
        return Response.noContent().build();
    }
}