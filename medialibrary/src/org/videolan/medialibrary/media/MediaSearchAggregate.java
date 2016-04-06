package org.videolan.medialibrary.media;

public class MediaSearchAggregate {

    private final MediaWrapper[] episodes, movies, others, tracks;

    public MediaSearchAggregate(MediaWrapper[] episodes, MediaWrapper[] movies, MediaWrapper[] others, MediaWrapper[] tracks) {
        this.episodes = episodes;
        this.movies = movies;
        this.others = others;
        this.tracks = tracks;
    }

    public MediaWrapper[] getEpisodes() {
        return episodes;
    }

    public MediaWrapper[] getMovies() {
        return movies;
    }

    public MediaWrapper[] getOthers() {
        return others;
    }

    public MediaWrapper[] getTracks() {
        return tracks;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (episodes != null && episodes.length > 0) {
            sb.append("Episodes:\n");
            for (MediaWrapper episode : episodes)
                sb.append(episode.getTitle()).append("\n");
        }
        if (movies != null && movies.length > 0) {
            sb.append("Movies:\n");
            for (MediaWrapper movie : movies)
                sb.append(movie.getTitle()).append("\n");
        }
        if (tracks != null && tracks.length > 0) {
            sb.append("Tracks:\n");
            for (MediaWrapper track : tracks)
                sb.append(track.getTitle()).append("\n");
        }
        if (others != null && others.length > 0) {
            sb.append("Others:\n");
            for (MediaWrapper other : others)
                sb.append(other.getTitle()).append("\n");
        }
        return sb.toString();
    }
}
