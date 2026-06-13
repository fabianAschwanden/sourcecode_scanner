package ch.example.app.adapter.in.rest;

import ch.example.app.adapter.in.rest.dto.CreateNoteRequest;
import ch.example.app.adapter.in.rest.dto.NoteDto;
import ch.example.app.domain.port.in.CreateNote;
import ch.example.app.domain.port.in.FindNotes;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.UUID;

/** Driving Adapter — übersetzt HTTP auf die Use-Case-Ports, keine Geschäftslogik. */
@Path("/api/notes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NoteResource {

    private final CreateNote createNote;
    private final FindNotes findNotes;

    public NoteResource(CreateNote createNote, FindNotes findNotes) {
        this.createNote = createNote;
        this.findNotes = findNotes;
    }

    @GET
    public List<NoteDto> list() {
        return findNotes.all().stream().map(NoteDto::from).toList();
    }

    @GET
    @Path("{id}")
    public NoteDto get(@PathParam("id") UUID id) {
        return findNotes.byId(id).map(NoteDto::from).orElseThrow(NotFoundException::new);
    }

    @POST
    public Response create(@Valid CreateNoteRequest request, @Context UriInfo uriInfo) {
        var note = createNote.create(request.title(), request.body());
        var location = uriInfo.getAbsolutePathBuilder().path(note.id().toString()).build();
        return Response.created(location).entity(NoteDto.from(note)).build();
    }
}
